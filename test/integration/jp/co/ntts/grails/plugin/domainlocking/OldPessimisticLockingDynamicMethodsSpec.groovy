package jp.co.ntts.grails.plugin.domainlocking

import grails.plugin.spock.IntegrationSpec
import org.springframework.dao.OptimisticLockingFailureException
import test.TestDomain

class OldPessimisticLockingDynamicMethodsSpec extends IntegrationSpec {

    def testDomain
    def handledTestDomains = []

    private newSavedTestDomain(id) {
        new TestDomain(value: "TEST_DOMAIN_${id}").save(flush: true, failOnError: true)
    }

    def setup() {
        testDomain = newSavedTestDomain(1)

        // テストしやすいようにデフォルト値から変更しておく
        OldPessimisticLockingUtil.retryCount = 3
        OldPessimisticLockingUtil.interval = 0
    }

    def cleanup() {
        TestDomain.list()*.delete(flush: true)
    }

    def "withLockAndRetry: variation of retryCount and returnValue"() {
        when:
        def result = TestDomain.withLockAndRetry(testDomain.id) { lockedTestDomain ->
            handledTestDomains << lockedTestDomain
            if (handledTestDomains.size() <= failingTimes) {
                throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
            }
            return "OK"
        }

        then:
        result == expectedResult
        handledTestDomains.size() == handledDomainCount

        where:
        failingTimes | handledDomainCount | expectedResult
        0            | 1                  | "OK"
        1            | 2                  | "OK"
        2            | 3                  | "OK"
        3            | 3                  | null
    }

    def "withLockAndRetry: interval can be changed"() {
        setup:
        OldPessimisticLockingUtil.interval = 1000 // msec
        def previousTime = new Date().time // msec
        def wrapTimes = []

        when:
        TestDomain.withLockAndRetry(testDomain.id) { lockedTestDomain ->
            def currentTime = new Date().time // msec
            wrapTimes << (currentTime - previousTime)
            previousTime = currentTime
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST") // 処理失敗にして全て回す
        }

        then:
        wrapTimes.size() == 3

        // 2回目以降は1つ前の実行からinterval間隔以上経っていること。
        // ただし、intervalよりも長すぎてもNG(とはいえ、サーバ性能によって成功したり
        // 失敗したりするのは良くないため、かなり余裕を持たせた)。
        wrapTimes[1..2].every { (1000 <= it) && (it < 5000) }
    }

    def "withLockAndRetry: when unexpected exception occures"() {
        when:
        TestDomain.withLockAndRetry(testDomain.id) { lockedTestDomain ->
            throw new IOException("EXCEPTION_FOR_TEST")
        }

        then:
        IOException e = thrown()
        e.message == "EXCEPTION_FOR_TEST"
    }

    def "withLockAndRetry: when target has been updated at another session"() {
        setup:
        TestDomain.withNewSession {
            def workTestDomain = TestDomain.get(testDomain.id)
            workTestDomain.value = "123456789"
            workTestDomain.save(flush: true)
            assert workTestDomain.version == 1
        }
        // メインセッションでのtestDomainはまだ更新されてない。
        assert testDomain.version == 0

        expect:
        TestDomain.withLockAndRetry(testDomain.id) { lockedTestDomain ->
            // 別セッションでの更新もきちんと反映した上でクロージャが実行される。
            assert lockedTestDomain.version == 1
            assert lockedTestDomain.value == "123456789"
            lockedTestDomain.value = "1234567890"
            lockedTestDomain.save(flush: true)
        }
    }

    def "withLockAndRetry: target is not found when deleted at another session"() {
        setup:
        TestDomain.withNewSession {
            // 別セッションでtestDomainを削除しておく。
            TestDomain.get(testDomain.id).delete(flush: true)
            assert TestDomain.count() == 0
        }

        when:
        def result = TestDomain.withLockAndRetry(testDomain.id) { lockedTestDomain ->
            // 対象が存在しない場合はクロージャ自体が実行されない
            assert false
        }

        then:
        result == null
    }

    def "withLockAndRetry: target is not found when deleted at first invocation of closure"() {
        when:
        def result = TestDomain.withLockAndRetry(testDomain.id) { lockedTestDomain ->
            handledTestDomains << lockedTestDomain

            // 1回目で削除する。2回目で対象ドメインが見つからなくなることを期待している。
            // 別のスレッドによってリトライ途中で対象が削除された、という想定。
            TestDomain.withNewSession {
                // 別セッションでtestDomainを削除しておく。
                TestDomain.get(testDomain.id).delete(flush: true)
                assert TestDomain.count() == 0
            }

            // 処理失敗にして次に回す。
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        then:
        result == null
        handledTestDomains.size() == 1
    }
}
