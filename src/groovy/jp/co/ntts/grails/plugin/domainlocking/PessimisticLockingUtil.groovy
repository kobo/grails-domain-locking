package jp.co.ntts.grails.plugin.domainlocking

import org.springframework.dao.OptimisticLockingFailureException
import java.lang.reflect.UndeclaredThrowableException
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * ドメイン操作の競合を回避して確実に更新するためのヘルパクラスです。
 * 事前の悲観的ロック取得、処理中の楽観的違反検出、リトライ、の3つによって可能な限り更新を成功に導きます。
 * 競合時に先行するセッションの情報を強制的に上書きするため使いどころに注意が必要です。
 * 競合発生時の対処を自律的に判断できないWebAPI向けに利用することを想定しています。
 */
class PessimisticLockingUtil {

    private static final Log log = LogFactory.getLog(PessimisticLockingUtil.getClass())

    // TODO properties化してstaticで持つのをやめる
    static int retryCount = 1
    static int interval = 0

    /**
     * ドメインオブジェクトの更新処理を確実に実行します。
     * 引数で指定したドメイントオブジェクトに対して悲観的ロックを取得し、他のセッションとの競合を防ぎます。
     * 別セッションによって、対象ドメインオブジェクトが削除された場合は、リトライ最大回数に達する前に終了します。
     * 色々なレイヤで使用するとロック＆リトライが多重で行われるなど収拾が付かなくなる恐れがあるため、
     * コントローラ等のレイヤでのみ利用することを推奨します。
     *
     * @param lockingDomainClass ロック対象のドメインクラス
     * @param lockingDomainId ロック対象のドメインオブジェクトのID
     * @param closure 実行したいドメインオブジェクト操作を含むクロージャ。クロージャ引数にロック取得に成功したドメインオブジェクトが渡されます。
     * @return クロージャ処理が正常に実行できた場合、クロージャの戻り値をそのまま返します。
     */
    static withLockAndRetry(Class lockingDomainClass, long lockingDomainId, Closure closure) {
        for (num in 1..retryCount) {
            boolean succeed = false
            try {
                def result
                boolean notFoundDomainRecord = false
                lockingDomainClass.withTransaction {
                    // 悲観的ロックを取得する。
                    // これがないとデッドロックが発生する場合があるため、現状では必要。
                    // これまでのセッションキャッシュをクリアして、ロックを獲得したときに確実に最新の情報がとれるようにする。
                    lockingDomainClass.withSession{ it.clear() }
                    def lockedDomain = lockingDomainClass.lock(lockingDomainId)
                    if (!lockedDomain) {
                        notFoundDomainRecord = true
                        return // withTransactionを抜ける
                    }
                    log.debug "悲観的ロックを獲得しました。: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}, version=${lockedDomain.version}"

                    // クロージャを実行する。
                    // 悲観的ロックの獲得に成功したドメインオブジェクトをクロージャ引数として渡す。
                    result = closure.call(lockedDomain)
                }

                // 対象が存在しない場合にはリトライせずにここで終了する。
                if (notFoundDomainRecord) {
                    log.debug "対象ドメインレコードが存在しません。: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}"
                    return null
                }

                // 対象が存在して例外も発生しない場合は正常終了する。
                log.debug "ロック＆リトライによる処理が成功しました。: ${num}回目/全${retryCount}回"
                return result

            } catch (UndeclaredThrowableException e) {
                // クロージャ処理で例外が発生していた場合は、呼出元にそのままスローする。
                log.debug "ロック＆リトライによる処理で例外が発生しました。: ${num}回目/全${retryCount}回, causedMessage=${e.cause?.message}"
                throw e.cause

            } catch (OptimisticLockingFailureException e) {
                log.debug "楽観的ロックで競合が発生しました。: ${e.message}"
            }

            // 指定の秒数だけ待機する。
            Thread.sleep interval
        }
        log.debug "ロック＆リトライによる全ての処理が失敗しました。: 全${retryCount}回"
        return null
    }
}

