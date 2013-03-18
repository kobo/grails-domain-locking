package jp.co.ntts.grails.plugin.domainlocking
import grails.plugin.spock.IntegrationSpec

class UtilSpec extends IntegrationSpec {

    def "convertToLong: converts to long or null from any types"() {
        when:
        def result = Util.convertToLong(from)

        then:
        result == to

        and:
        if (result != null) {
            assert result instanceof Long
        }

        where:
        from         | to
        1            | 1L
        1F           | 1L
        1L           | 1L
        1G           | 1L
        "1"          | 1L
        "123"        | 123L
        "NOT NUMBER" | null
        ""           | null
        null         | null
        new Object() | null
    }
}
