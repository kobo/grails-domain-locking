package jp.co.ntts.grails.plugin.domainlocking

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException

/**
 * ドメイン操作の競合時の処理を統一的に処理するためのヘルパクラスです。
 * 競合発生時にリトライ等はせずに、単純に指定された失敗ハンドラを使って処理を行います。
 * 競合時に先行するセッションの情報を誤って強制的に上書きすることを防げます。
 * ユーザに競合発生を通知して自律的な判断を促せるGUI向けに利用することを想定しています。
 */
@Deprecated
class OldOptimisticLockingUtil {

    private static final Log LOG = LogFactory.getLog(OldOptimisticLockingUtil)

    /**
     * ドメインオブジェクトの更新処理を実行します。
     * 楽観的ロック失敗時は指定した失敗ハンドラを実行します。
     *
     * @param domain 更新対象のドメインオブジェクト
     * @param persistentVersion 現在DBに保存されているバージョン
     * @param modificationBaseVersion 更新処理のベースとなったバージョン
     * @param updateClosure 実行したいドメインオブジェクト操作を含むクロージャ
     * @param failureHandler 失敗ハンドラ
     * @return クロージャ処理が正常に実行できた場合、クロージャの戻り値をそのまま返します。
     *         失敗ハンドラが実行された場合は、失敗ハンドラの戻り値をそのまま返します。
     */
    static withFailureHandler(domain, persistentVersion, modificationBaseVersion, Closure updateClosure, Closure failureHandler) {
        tryUpdate(domain, persistentVersion, modificationBaseVersion, updateClosure, failureHandler)
    }

    /**
     * ドメインオブジェクトの更新処理を実行します。
     * 楽観的ロック失敗時はデフォルトの失敗ハンドラを実行します。
     *
     * @param domain 更新対象のドメインオブジェクト
     * @param persistentVersion 現在DBに保存されているバージョン
     * @param modificationBaseVersion 更新処理のベースとなったバージョン
     * @param updateClosure 実行したいドメインオブジェクト操作を含むクロージャ
     * @return クロージャ処理が正常に実行できた場合、クロージャの戻り値をそのまま返します。
     *         失敗ハンドラが実行された場合は、失敗ハンドラの戻り値をそのまま返します。
     */
    static withDefaultFailureHandler(domain, persistentVersion, modificationBaseVersion, Closure updateClosure) {
        tryUpdate(domain, persistentVersion, modificationBaseVersion, updateClosure, setOptimisticLockingFailureToRejectValue)
    }

    /**
     * ドメインオブジェクトの更新処理を実行します。
     * 楽観的ロック失敗時はデフォルトの失敗ハンドラと指定した失敗ハンドラを順番に実行します。
     *
     * @param domain 更新対象のドメインオブジェクト
     * @param persistentVersion 現在DBに保存されているバージョン
     * @param modificationBaseVersion 更新処理のベースとなったバージョン
     * @param updateClosure 実行したいドメインオブジェクト操作を含むクロージャ
     * @param extraFailureHandler 追加の失敗ハンドラ
     * @return クロージャ処理が正常に実行できた場合、クロージャの戻り値をそのまま返します。
     *         失敗ハンドラが実行された場合は、失敗ハンドラの戻り値をそのまま返します。
     */
    static withExtraFailureHandler(domain, persistentVersion, modificationBaseVersion, Closure updateClosure, Closure extraFailureHandler) {
        tryUpdate(domain, persistentVersion, modificationBaseVersion, updateClosure, { failedDomain ->
            setOptimisticLockingFailureToRejectValue.call(failedDomain)
            extraFailureHandler.call(failedDomain)
        })
    }

    /**
     * ドメインオブジェクトに楽観的ロックの失敗情報を追加します。
     * デフォルトの失敗ハンドラとして利用されます。
     * @return 戻り値は必ずnull
     */
    static setOptimisticLockingFailureToRejectValue = { domain ->
        def domainClassName = domain.getClass().simpleName
        domain.errors.rejectValue("version", "default.optimistic.locking.failure",
            [domainClassName] as Object[],
            "Another user has updated this ${domainClassName} while you were editing")
        return null
    }

    private static tryUpdate(domain, persistentVersion, modificationBaseVersion, Closure updateClosure, Closure failureHandler) {
        Long persistentVersionAsLong = convertToLong(persistentVersion)
        Long modificationBaseVersionAsLong = convertToLong(modificationBaseVersion)
        if (persistentVersionAsLong != null && modificationBaseVersion != null) {
            if (persistentVersionAsLong > modificationBaseVersionAsLong) {
                LOG.debug "他のセッションによってバージョンが更新されています。: persistentVersion=${persistentVersionAsLong}, modificationBaseVersion=${modificationBaseVersionAsLong}"
                return failureHandler(domain)
            }
        }
        try {
            // TODO クロージャ内でトランザクションが閉じていない場合は、トランザクションまたはセッションがクローズするときに例外が発生する可能性がある。
            // もちろんその例外はここでは補足できない。

            return updateClosure.call()

        } catch (DataIntegrityViolationException e) {
            LOG.warn "制約違反が発生しました。: ${e.message}"
            return failureHandler(domain)
        } catch (OptimisticLockingFailureException e) {
            LOG.warn "楽観的ロックで競合が発生しました。: ${e.message}"
            return failureHandler(domain)
        }
    }

    private static Long convertToLong(number) {
        switch (number) {
            case Number:
                return number as Long
            case String:
                return number.isLong() ? number.toLong() : null
            case null:
            default:
                return null
        }
    }
}

