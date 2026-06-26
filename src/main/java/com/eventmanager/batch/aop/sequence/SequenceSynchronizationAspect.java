package com.eventmanager.batch.aop.sequence;

import com.eventmanager.batch.service.SequenceSynchronizationService;
import jakarta.persistence.Table;
import jakarta.validation.ConstraintViolationException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Aspect for handling duplicate key constraint violations by synchronizing
 * the affected table's per-table id sequence, clearing entity IDs, and retrying the operation.
 * 
 * This provides secondary protection against duplicate key errors. Primary prevention
 * should be done at the service level by clearing entity IDs before save operations.
 * 
 * Pattern:
 * 1. Catches duplicate key violations
 * 2. Synchronizes the table's {table}_id_seq (or batch_job_execution_log_id_seq)
 * 3. Clears entity ID(s) from the save operation arguments
 * 4. Retries the save operation once
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class SequenceSynchronizationAspect {

    private static final Pattern TABLE_FROM_PKEY = Pattern.compile(
        "(?:public\\.)?([a-z0-9_]+)_pkey",
        Pattern.CASE_INSENSITIVE
    );

    private final SequenceSynchronizationService sequenceSynchronizationService;

    @Pointcut(
        "execution(* org.springframework.data.repository.CrudRepository.save(..)) || " +
        "execution(* org.springframework.data.jpa.repository.JpaRepository.save(..)) || " +
        "execution(* org.springframework.data.jpa.repository.JpaRepository.saveAndFlush(..)) || " +
        "execution(* org.springframework.data.repository.CrudRepository.saveAll(..))"
    )
    public void repositorySavePointcut() {
        // Pointcut for repository save operations
    }

    @Pointcut("within(com.eventmanager.batch.repository..*)")
    public void repositoryPointcut() {
        // Pointcut for repositories in this project
    }

    /**
     * Handles duplicate key violations by synchronizing sequence and clearing entity IDs.
     * 
     * According to duplicate-key-prevention.mdc rule:
     * - Catches DataIntegrityViolationException and ConstraintViolationException
     * - Detects duplicate key violations on primary keys
     * - Synchronizes sequence automatically
     * - Clears entity ID before retry
     * - Retries save operation once
     */
    @Around("repositorySavePointcut() && repositoryPointcut()")
    public Object handleDuplicateKeyViolation(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String declaringType = joinPoint.getSignature().getDeclaringTypeName();
        Object[] args = joinPoint.getArgs();

        try {
            return joinPoint.proceed();
        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
            if (isDuplicateKeyViolation(e)) {
                String tableName = resolveTableName(args, e);
                log.warn(
                    "Duplicate key violation detected in {}.{}() (table={}). " +
                    "Syncing per-table sequence, clearing entity ID(s), and retrying...",
                    declaringType,
                    methodName,
                    tableName,
                    e
                );

                try {
                    Long newSequenceValue;
                    if (isBatchJobExecutionLogError(e)) {
                        newSequenceValue = sequenceSynchronizationService.synchronizeBatchJobExecutionLogSequence();
                    } else if (tableName != null) {
                        newSequenceValue = sequenceSynchronizationService.synchronizeTableSequence(tableName);
                    } else {
                        var results = sequenceSynchronizationService.synchronizeAllTableSequences();
                        newSequenceValue = results.isEmpty()
                            ? null
                            : results.values().stream().max(Long::compareTo).orElse(null);
                    }

                    log.info(
                        "Sequence synchronized (table={}, value={}). Clearing entity ID(s) and retrying {}.{}()...",
                        tableName,
                        newSequenceValue,
                        declaringType,
                        methodName
                    );

                    // Step 2: Clear entity ID(s) from arguments in-place before retry
                    // This prevents Hibernate from using INSERT with explicit ID
                    boolean idsCleared = clearEntityIdsInPlace(args);
                    
                    if (idsCleared) {
                        log.debug(
                            "Cleared entity ID(s) from {}.{}() arguments before retry",
                            declaringType,
                            methodName
                        );
                    }

                    // Step 3: Retry with cleared IDs (entities modified in-place)
                    Object result = joinPoint.proceed();

                    log.info(
                        "Successfully completed {}.{}() after sequence synchronization and ID clearing",
                        declaringType,
                        methodName
                    );
                    return result;
                } catch (Exception retryException) {
                    log.error(
                        "Failed to execute {}.{}() even after sequence synchronization and ID clearing",
                        declaringType,
                        methodName,
                        retryException
                    );
                    throw new RuntimeException(
                        String.format(
                            "Failed to save entity due to duplicate key constraint. " +
                                "Sequence synchronization and ID clearing attempted but failed: %s",
                            retryException.getMessage()
                        ),
                        retryException
                    );
                }
            }

            throw e;
        }
    }

    /**
     * Clears entity IDs from save operation arguments in-place.
     * Handles both single entity and collection cases.
     * 
     * According to duplicate-key-prevention.mdc rule:
     * - Clears entity ID before retry
     * - Forces Hibernate to use sequence generator instead of INSERT with explicit ID
     * 
     * @param args Method arguments (modified in-place)
     * @return true if any IDs were cleared, false otherwise
     */
    private boolean clearEntityIdsInPlace(Object[] args) {
        if (args == null || args.length == 0) {
            return false;
        }

        boolean anyCleared = false;

        for (Object arg : args) {
            if (arg == null) {
                continue;
            }

            // Handle single entity
            if (isEntityWithId(arg)) {
                if (clearEntityId(arg)) {
                    anyCleared = true;
                }
            }
            // Handle collections (saveAll case)
            else if (arg instanceof Collection<?> collection) {
                if (clearCollectionEntityIds(collection)) {
                    anyCleared = true;
                }
            }
        }

        return anyCleared;
    }

    /**
     * Clears ID from a single entity object using reflection.
     * Modifies the entity in-place.
     * 
     * @param entity Entity object (modified in-place)
     * @return true if ID was cleared, false if ID was already null or couldn't be cleared
     */
    private boolean clearEntityId(Object entity) {
        try {
            // Try to find getId() method
            Method getIdMethod = findMethod(entity.getClass(), "getId");
            if (getIdMethod == null) {
                return false; // No getId method, can't clear ID
            }

            // Check if ID is set
            Object currentId = getIdMethod.invoke(entity);
            if (currentId == null) {
                return false; // ID already null, no need to clear
            }

            // Try to find setId() method
            Method setIdMethod = findMethod(entity.getClass(), "setId", Long.class);
            if (setIdMethod == null) {
                // Try with Object.class for generic setters
                setIdMethod = findMethod(entity.getClass(), "setId", Object.class);
            }
            
            if (setIdMethod == null) {
                log.debug("Entity {} has ID set but no setId() method found. Cannot clear ID.", 
                    entity.getClass().getSimpleName());
                return false;
            }

            // Clear the ID
            setIdMethod.invoke(entity, (Object) null);
            log.debug(
                "Cleared ID {} from entity {} before retry",
                currentId,
                entity.getClass().getSimpleName()
            );
            
            return true;
        } catch (Exception e) {
            log.warn(
                "Failed to clear ID from entity {}: {}. Proceeding without ID clearing.",
                entity.getClass().getSimpleName(),
                e.getMessage()
            );
            return false;
        }
    }

    /**
     * Clears IDs from all entities in a collection in-place.
     * 
     * @param collection Collection of entities (modified in-place)
     * @return true if any IDs were cleared, false otherwise
     */
    private boolean clearCollectionEntityIds(Collection<?> collection) {
        boolean anyCleared = false;

        for (Object item : collection) {
            if (item == null) {
                continue;
            }

            if (clearEntityId(item)) {
                anyCleared = true;
            }
        }

        return anyCleared;
    }

    /**
     * Checks if an object is an entity with an ID field.
     * 
     * @param obj Object to check
     * @return true if object appears to be an entity with ID
     */
    private boolean isEntityWithId(Object obj) {
        if (obj == null) {
            return false;
        }
        
        // Check for getId() method
        return findMethod(obj.getClass(), "getId") != null;
    }

    /**
     * Finds a method by name and parameter types using reflection.
     * 
     * @param clazz Class to search
     * @param methodName Method name
     * @param paramTypes Parameter types (varargs)
     * @return Method if found, null otherwise
     */
    private Method findMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // Try to find with any parameter types
            try {
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if (method.getName().equals(methodName) && 
                        method.getParameterCount() == paramTypes.length) {
                        // Check if parameter types are compatible
                        Class<?>[] methodParamTypes = method.getParameterTypes();
                        boolean compatible = true;
                        for (int i = 0; i < paramTypes.length; i++) {
                            if (!methodParamTypes[i].isAssignableFrom(paramTypes[i])) {
                                compatible = false;
                                break;
                            }
                        }
                        if (compatible) {
                            return method;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Ignore and return null
            }
            return null;
        }
    }

    private String resolveTableName(Object[] args, Exception e) {
        String fromConstraint = extractTableFromConstraintMessage(e);
        if (fromConstraint != null) {
            return fromConstraint;
        }
        if (args != null && args.length > 0) {
            Object first = args[0];
            if (first instanceof Collection<?> collection && !collection.isEmpty()) {
                first = collection.iterator().next();
            }
            if (first != null) {
                Table table = first.getClass().getAnnotation(Table.class);
                if (table != null && table.name() != null && !table.name().isBlank()) {
                    return table.name();
                }
            }
        }
        return null;
    }

    private String extractTableFromConstraintMessage(Exception e) {
        String combined = buildMessageChain(e);
        Matcher m = TABLE_FROM_PKEY.matcher(combined);
        if (m.find()) {
            return m.group(1).toLowerCase();
        }
        return null;
    }

    private String buildMessageChain(Exception e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(' ');
            }
            t = t.getCause();
        }
        return sb.toString().toLowerCase();
    }

    /**
     * Checks if an exception is a duplicate key violation on a primary key.
     * 
     * @param e Exception to check
     * @return true if it's a duplicate key violation on primary key
     */
    private boolean isDuplicateKeyViolation(Exception e) {
        if (e == null) {
            return false;
        }

        String message = e.getMessage();
        if (message == null) {
            message = "";
        }
        message = message.toLowerCase();

        // Check cause message as well
        String causeMessage = "";
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            causeMessage = e.getCause().getMessage().toLowerCase();
        }

        // Check for duplicate key violation patterns
        boolean isDuplicateKey = message.contains("duplicate key value violates unique constraint") ||
                                 causeMessage.contains("duplicate key value violates unique constraint") ||
                                 message.contains("duplicate key") ||
                                 causeMessage.contains("duplicate key");

        // Check for primary key constraint
        boolean isPrimaryKey = message.contains("pkey") ||
                               causeMessage.contains("pkey") ||
                               message.contains("primary key") ||
                               causeMessage.contains("primary key") ||
                               message.contains("(id)=") ||
                               causeMessage.contains("(id)=");

        return isDuplicateKey && isPrimaryKey;
    }

    /**
     * Checks if the exception is related to batch_job_execution_log table.
     * 
     * @param e Exception to check
     * @return true if error is for batch_job_execution_log table
     */
    private boolean isBatchJobExecutionLogError(Exception e) {
        if (e == null) {
            return false;
        }

        String message = e.getMessage();
        if (message == null) {
            message = "";
        }
        message = message.toLowerCase();

        // Check cause message as well
        String causeMessage = "";
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            causeMessage = e.getCause().getMessage().toLowerCase();
        }

        // Check if error mentions batch_job_execution_log
        return message.contains("batch_job_execution_log") ||
               causeMessage.contains("batch_job_execution_log");
    }
}
