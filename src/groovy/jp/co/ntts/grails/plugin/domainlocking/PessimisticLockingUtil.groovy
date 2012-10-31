package jp.co.ntts.grails.plugin.domainlocking

import groovy.util.logging.Commons

/**
 * 悲観的ロックを利用してドメイン操作の競合を回避して確実に更新します。
 */
@Commons
class PessimisticLockingUtil {

    /**
     * 引数で指定したドメイントオブジェクトに対して悲観的ロックを取得し、他のセッションとの競合を防ぎます。
     * <p>
     * メイン処理を実行する前に、セッションのflushとclearが実行されます。
     *
     * @param lockingDomainClass ロック対象のドメインクラス
     * @param lockingDomainId ロック対象のドメインオブジェクトのID
     * @param dslClosure DSL
     * @return メイン処理のクロージャの戻り値。対象が見つからない場合は、onNotFoundクロージャの戻り値。
     */
    static withPessimisticLock(Class lockingDomainClass, Long lockingDomainId, Closure dslClosure) {
        shouldNotNull(lockingDomainClass: lockingDomainClass, lockingDomainId: lockingDomainId, dslClosure: dslClosure)

        // DSLをパースする。
        def dsl = PessimisticLockingDsl.parse(dslClosure)

        // 1次キャッシュとDBの状態に差分がある場合(他のセッションによる変更・削除後など)、
        // OptimisticLockingFailureExceptionが発生してしまう。これを防止するために、
        // 強制的にflush & clearする。
        lockingDomainClass.withSession { session ->
            session.flush()
            session.clear()
        }

        // 悲観的ロックを取得する。
        def lockedDomain = lockingDomainClass.lock(lockingDomainId)
        if (lockedDomain == null) {
            log.debug "Target not found: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}"
            return dsl.onNotFoundClosure.call(lockingDomainId)
        }
        log.debug "Acquired pessimistic lock: domainClass=${lockingDomainClass.name}, id=${lockingDomainId}, version=${lockedDomain.version}"

        // クロージャを実行する。
        return dsl.mainClosure.call(lockedDomain)
    }

    private static shouldNotNull(argMaps) {
        argMaps.each { name, value ->
            if (value == null) throw new IllegalArgumentException("${name} should not be null.")
        }
    }

}

