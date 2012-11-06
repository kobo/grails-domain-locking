package jp.co.ntts.grails.plugin.domainlocking

import grails.plugin.spock.IntegrationSpec
import test.TestDomain

class OldOptimisticLockingDynamicMethodsSpec extends IntegrationSpec {

    def testDomain

    def setup() {
        testDomain = new TestDomain() // necessary to be saved
    }

    def "withFailureHandler: 内部のtryUpdateに正しくディスパッチできているかどうか"() {
        when:
        def result = testDomain.withFailureHandler(persistentVersion, modificationBaseVersion, {->
            return "OK"
        }                                          , { domain ->
            assert domain == testDomain
            return "NG"
        })

        then:
        result == expected

        where:
        persistentVersion | modificationBaseVersion | expected
        0                 | 0                       | "OK"
        0                 | 1                       | "OK"
        1                 | 0                       | "NG"
    }

    def "withDefaultFailureHandler: 内部のtryUpdateに正しくディスパッチできているかどうか"() {
        when:
        def result = testDomain.withDefaultFailureHandler(persistentVersion, modificationBaseVersion) {->
            return "OK"
        }

        then:
        result == expected
        if (result == null) {
            assert testDomain.hasErrors() == true
        }

        where:
        persistentVersion | modificationBaseVersion | expected
        0                 | 0                       | "OK"
        0                 | 1                       | "OK"
        1                 | 0                       | null // デフォルト失敗ハンドラはnullを返す
    }

    def "withExtraFailureHandler: 内部のtryUpdateに正しくディスパッチできているかどうか"() {
        when:
        def result = testDomain.withExtraFailureHandler(persistentVersion, modificationBaseVersion, {->
            return "OK"
        }                                               , { domain ->
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
        0                 | 0                       | "OK"
        0                 | 1                       | "OK"
        1                 | 0                       | "NG"
    }
}
