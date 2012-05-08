package jp.co.ntts.grails.plugin.domainlocking

import grails.test.*
import org.springframework.dao.OptimisticLockingFailureException
import test.*

class StrictDomainUpdaterTests extends GroovyTestCase {

    def strictDomainUpdater
    def testDomain

    private newSavedTestDomain(id) {
        new TestDomain(value: "FOOBAR_1").save(flush:true, failOnError:true)
    }

    protected void setUp() {
        super.setUp()

        testDomain = newSavedTestDomain(1)

        // テストしやすいようにデフォルト値から変更しておく
        strictDomainUpdater.retryCount = 3
        strictDomainUpdater.interval == 0
    }

    protected void tearDown() {
        super.tearDown()
    }

    void testWithLockAndRetry_retryCountAndReturnValue_succeedFirst() {
        // Setup
        def testDomains = []

        // Exercise
        def result = strictDomainUpdater.withLockAndRetry(TestDomain, testDomain.id) { lockedTestDomain ->
            testDomains << lockedTestDomain
            return "OK"
        }

        // Verify
        assert result == "OK"
        assert testDomains*.id == [testDomain.id] * 1
    }

    void testWithLockAndRetry_retryCountAndReturnValue_succeedSecond() {
        // Setup
        def testDomains = []

        // Exercise
        def result = strictDomainUpdater.withLockAndRetry(TestDomain, testDomain.id) { lockedTestDomain ->
            testDomains << lockedTestDomain
            if (testDomains.size() <= 1) { // 1回目は失敗
                throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
            }
            return "OK"
        }

        // Verify
        assert result == "OK"
        assert testDomains*.id == [testDomain.id] * 2
    }

    void testWithLockAndRetry_retryCountAndReturnValue_succeedLast() {
        // Setup
        def testDomains = []

        // Exercise
        def result = strictDomainUpdater.withLockAndRetry(TestDomain, testDomain.id) { lockedTestDomain ->
            testDomains << lockedTestDomain
            if (testDomains.size() <= 2) { // 2回目までは失敗
                throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
            }
            return "OK"
        }

        // Verify
        assert result == "OK"
        assert testDomains*.id == [testDomain.id] * 3
    }

    void testWithLockAndRetry_retryCountAndReturnValue_failedAll() {
        // Setup
        def testDomains = []

        // Exercise
        def result = strictDomainUpdater.withLockAndRetry(TestDomain, testDomain.id) { lockedTestDomain ->
            testDomains << lockedTestDomain
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        // Verify
        assert result == null
        assert testDomains*.id == [testDomain.id] * 3
    }

    void testWithLockAndRetry_interval() {
        // Setup
        strictDomainUpdater.interval = 1000 // msec
        def previousTime = new Date().time // msec
        def wrapTimes = []

        // Exercise
        strictDomainUpdater.withLockAndRetry(TestDomain, testDomain.id) { lockedTestDomain ->
            def currentTime = new Date().time // msec
            wrapTimes << (currentTime - previousTime)
            previousTime = currentTime
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST") // 処理失敗にして全て回す
        }

        // Verify
        assert wrapTimes.size() == 3

        // 2回目以降は1つ前の実行からinterval間隔以上経っていること。
        // ただし、intervalよりも長すぎてもNG(とはいえ、サーバ性能によって成功したり
        // 失敗したりするのは良くないため、かなり余裕を持たせた)。
        assert wrapTimes[1..2].every{ (1000 <= it) && (it < 5000) }
    }

    void testWithLockAndRetry_exception_unexpected() {
        // Exercise & Verify
        try {
            strictDomainUpdater.withLockAndRetry(TestDomain, testDomain.id) { lockedTestDomain ->
                throw new IOException("EXCEPTION_FOR_TEST")
            }
            assert false

        } catch (IOException e) {
            assert e.message == "EXCEPTION_FOR_TEST"
        }
    }

    void testWithLockAndRetry_updateAtAnotherSession() {
        // Setup
        TestDomain.withNewSession {
            def workTestDomain = TestDomain.get(testDomain.id)
            workTestDomain.value = "123456789"
            workTestDomain.save(flush:true)
            assert workTestDomain.version == 1
        }
        // メインセッションでのtestDomainはまだ更新されてない。
        assert testDomain.version == 0

        // Exercise & Verify
        strictDomainUpdater.withLockAndRetry(TestDomain, testDomain.id) { lockedTestDomain ->
            assert lockedTestDomain.version == 1
            assert lockedTestDomain.value == "123456789"
            lockedTestDomain.value = "1234567890"
            lockedTestDomain.save(flush:true)
        }
    }

    void testWithLockAndRetry_targetNotFound_deleteAtAnotherSession() {
        // Setup
        def testDomains = []
        TestDomain.withNewSession {
            // 別セッションでtestDomainを削除しておく。
            TestDomain.get(testDomain.id).delete(flush:true)
            assert TestDomain.count() == 0
        }

        // Exercise
        def result = strictDomainUpdater.withLockAndRetry(TestDomain, testDomain.id) { lockedTestDomain ->
            testDomains << lockedTestDomain
        }

        // Verify
        assert result == null
        assert testDomains.size() == 0
    }

    void testWithLockAndRetry_targetNotFound_deleteAtFirstTrial() {
        // Setup
        def testDomains = []

        // Exercise
        def result = strictDomainUpdater.withLockAndRetry(TestDomain, testDomain.id) { lockedTestDomain ->
            testDomains << lockedTestDomain

            // 1回目で削除する。2回目で対象ドメインが見つからなくなることを期待している。
            // 別のスレッドによってリトライ途中で対象が削除された、という想定。
            TestDomain.withNewSession {
                // 別セッションでtestDomainを削除しておく。
                TestDomain.get(testDomain.id).delete(flush:true)
                assert TestDomain.count() == 0
            }

            // 処理失敗にして次に回す。
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }

        // Verify
        assert result == null
        assert testDomains*.id == [testDomain.id] * 1
    }
}
