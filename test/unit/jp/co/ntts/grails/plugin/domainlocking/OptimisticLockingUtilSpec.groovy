package jp.co.ntts.grails.plugin.domainlocking

import grails.plugin.spock.*
import spock.lang.*
import org.springframework.dao.OptimisticLockingFailureException
import test.*

@Mock(TestDomain)
class OptimisticLockingUtilSpec extends Specification {

    def testDomain

    def setup() {
        testDomain = new TestDomain()
    }

    def "tryUpdate: version比較: persistentVersionとmodificationBaseVersionが等しい場合、更新処理が実行される"() {
        when:
        def result = OptimisticLockingUtil.tryUpdate(testDomain, persistentVersion, modificationBaseVersion, { ->
            return "OK"
        }, { domain ->
            assert false
        })

        then:
        result == "OK"

        where:
        persistentVersion | modificationBaseVersion
                        0 |                       0
                        1 |                       1
                       10 |                      10
                      100 |                     100
    }

    def "tryUpdate: version比較: persistentVersionがmodificationBaseVersionよりも古い場合、更新処理が実行される"() {
        when:
        def result = OptimisticLockingUtil.tryUpdate(testDomain, persistentVersion, modificationBaseVersion, { ->
            return "OK"
        }, { domain ->
            assert false
        })

        then:
        result == "OK"

        where:
        persistentVersion | modificationBaseVersion
                        0 |                       1
                        0 |                      10
                        1 |                       2
                       10 |                      11
                      100 |                     101
    }

    def "tryUpdate: version比較: persistentVersionがmodificationBaseVersionよりも新しい場合、競合したと見なし、失敗ハンドラが実行される"() {
        when:
        def result = OptimisticLockingUtil.tryUpdate(testDomain, persistentVersion, modificationBaseVersion, { ->
            assert false : "このクロージャは実行されない"
            return "OK"
        }, { domain ->
            assert domain == testDomain
            return "NG"
        })

        then:
        result == "NG"

        where:
        persistentVersion | modificationBaseVersion
                        1 |                       0
                        2 |                       0
                        2 |                       1
                       11 |                      10
                      101 |                     100
    }

    def "tryUpdate: OptimisticLockingFailureExceptionが発生した場合、競合したと見なし、失敗ハンドラが実行される"() {
        when:
        def result = OptimisticLockingUtil.tryUpdate(testDomain, persistentVersion, modificationBaseVersion, { ->
            throw new OptimisticLockingFailureException("EXCEPTION_FOR_TEST")
        }, { domain ->
            assert domain == testDomain
            return "NG"
        })

        then:
        result == "NG"

        where:
        persistentVersion | modificationBaseVersion
                        1 |                       0
                        2 |                       0
                        2 |                       1
                       11 |                      10
                      101 |                     100
    }

    def "setOptimisticLockingFailureToRejectValue: デフォルトの失敗ハンドラではrejectValueが実行される"() {
        when:
        def result = OptimisticLockingUtil.setOptimisticLockingFailureToRejectValue(testDomain)

        then:
        result == null
        testDomain.hasErrors()
        def errors = testDomain.errors.getFieldErrors("version")
        errors.size() == 1
        errors[0].codes.toList().contains("default.optimistic.locking.failure")
    }

    def "convertToLong: longに変換する"() {
        when:
        def result = OptimisticLockingUtil.convertToLong(from)

        then:
        if (result != null) {
            assert result instanceof Long
        }
        result == to

        where:
                from |    to
                   1 |    1L
                  1L |    1L
                  1G |    1L
                 "1" |    1L
               "123" |  123L
        "NOT NUMBER" |  null
                  "" |  null
                null |  null
    }

    def "withFailureHandler: 内部のtryUpdateに正しくディスパッチできているかどうか"() {
        when:
        def result = OptimisticLockingUtil.withFailureHandler(testDomain, persistentVersion, modificationBaseVersion, { ->
            return "OK"
        }, { domain ->
            assert domain == testDomain
            return "NG"
        })

        then:
        result == expected

        where:
        persistentVersion | modificationBaseVersion | expected
                        0 |                       0 | "OK"
                        0 |                       1 | "OK"
                        1 |                       0 | "NG"
    }

    def "withDefaultFailureHandler: 内部のtryUpdateに正しくディスパッチできているかどうか"() {
        when:
        def result = OptimisticLockingUtil.withDefaultFailureHandler(testDomain, persistentVersion, modificationBaseVersion) { ->
            return "OK"
        }

        then:
        result == expected
        if (result == null) {
            assert testDomain.hasErrors() == true
        }

        where:
        persistentVersion | modificationBaseVersion | expected
                        0 |                       0 | "OK"
                        0 |                       1 | "OK"
                        1 |                       0 | null // デフォルト失敗ハンドラはnullを返す
    }

    def "withExtraFailureHandler: 内部のtryUpdateに正しくディスパッチできているかどうか"() {
        when:
        def result = OptimisticLockingUtil.withExtraFailureHandler(testDomain, persistentVersion, modificationBaseVersion, { ->
            return "OK"
        }, { domain ->
            assert domain == testDomain
            return "NG"
        })

        then:
        result == expected
        if (result == "NG") {
            // デフォルト失敗ハンドラによってエラーが追加されている
            assert testDomain.hasErrors() == true
        }

        where:
        persistentVersion | modificationBaseVersion | expected
                        0 |                       0 | "OK"
                        0 |                       1 | "OK"
                        1 |                       0 | "NG"
    }
}
