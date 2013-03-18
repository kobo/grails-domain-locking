grails.project.work.dir = 'target'
grails.project.source.level = 1.6

grails.project.dependency.resolution = {

    inherits 'global'
    log 'warn'
    legacyResolve false // whether to do a secondary resolve on plugin installation, not advised and here for backwards compatibility

    repositories {
        grailsCentral()
    }

    dependencies {
        test "org.spockframework:spock-grails-support:0.7-groovy-2.0"
    }

    plugins {
        build(":tomcat:$grailsVersion",
            ":release:2.2.0",
            ":rest-client-builder:1.0.3") {
            export = false
        }
        test(":hibernate:$grailsVersion") {
            export = false
        }
        test(":spock:0.7") {
            export = false
            exclude "spock-grails-support"
        }
    }
}
