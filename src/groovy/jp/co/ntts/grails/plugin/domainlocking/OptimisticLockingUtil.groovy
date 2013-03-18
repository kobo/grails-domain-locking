package jp.co.ntts.grails.plugin.domainlocking

import groovy.util.logging.Commons
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException

/**
 * ドメイン操作の楽観的ロック競合時の処理を統一的に処理します。
 */
@Commons
class OptimisticLockingUtil {

    /**
     * 楽観的ロックの下でドメインオブジェクトの更新処理を実行します。
     * <p>
     * 楽観的ロック失敗時は、対象ドメインクラスインスタンスのversionに対するFieldErrorを設定します。
     * その後、指定されている場合はその失敗ハンドラを実行します。
     * <p>
     * メイン処理を実行した後、セッションは自動的にflushされます。
     *
     * @param domain 更新対象のドメインクラスインスタンス
     * @param dslClosure DSL
     * @return メイン処理のクロージャの戻り値
     */
    static withOptimisticLock(domain, Closure dslClosure) {
        shouldNotNull(domain: domain, dslClosure: dslClosure)

        // DSLをパースする。
        def dsl = OptimisticLockingDsl.parse(dslClosure)

        // 統合された失敗ハンドラを作成する。
        def combinedFailureHandler = {
            bindFieldError(domain)
            return dsl.onFailureHandler?.call(domain)
        }

        // 変更前バージョンが明示指定されている場合は、ドメインの現在のversionと比較する。
        if (dsl.baseVersion != null) {
            if (isDifferentVersion(domain.version, dsl.baseVersion)) {
                log.debug "Version is already updated by other session: domainClass=${domain.class.name}, id=${domain.id}, persistentVersion=${domain.version}, modificationBaseVersion=${dsl.baseVersion}"
                return combinedFailureHandler.call()
            }
        }

        // メイン処理を実行する
        return executeMain(domain, dsl.mainClosure, combinedFailureHandler)
    }

    private static boolean isDifferentVersion(persistentVersion, modificationBaseVersion) {
        Long persistentVersionAsLong = convertToLong(persistentVersion)
        Long modificationBaseVersionAsLong = convertToLong(modificationBaseVersion)
        if (persistentVersionAsLong != null && modificationBaseVersion != null) {
            if (persistentVersionAsLong > modificationBaseVersionAsLong) {
                return true
            }
        }
        return false
    }

    private static executeMain(domain, Closure mainClosure, Closure failureHandler) {
        try {
            def result = mainClosure(domain)

            // ここでセッションのフラッシュを強制しないと、実際のSQL発行がこのクロージャ外で行われることになりキャッチできなくなってしまう。
            // To flush session only when mainClosure is succeed.
            // If this were missed, runtime exception couldn't occur here.
            // Instead, it would occur, for example, after an invocation of an action of a controller.
            domain.withSession { it.flush() }

            return result

        } catch (DataIntegrityViolationException e) {
            log.debug "Constraint violation occurred.", e
            return failureHandler(domain)
        } catch (OptimisticLockingFailureException e) {
            log.debug "Optimistic locking conflicted.", e
            return failureHandler(domain)
        }
    }

    private static void bindFieldError(domain) {
        def domainClassName = domain.getClass().simpleName
        domain.errors.rejectValue("version", "default.optimistic.locking.failure",
            [domainClassName] as Object[],
            "Another user has updated this ${domainClassName} while you were editing")
    }

    private static Long convertToLong(number) {
        switch (number) {
            case Number:
                return number as Long
            case String:
                return number.isLong() ? number.toLong() : null
            default:
                return null
        }
    }

    private static shouldNotNull(argMaps) {
        argMaps.each { name, value ->
            if (value == null) throw new IllegalArgumentException("${name} should not be null.")
        }
    }
}