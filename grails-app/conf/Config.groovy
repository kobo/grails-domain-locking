log4j = {
    error 'org.codehaus.groovy.grails',
        'org.springframework',
        'org.hibernate',
        'net.sf.ehcache.hibernate'

    environments {
        test {
            root {
                debug "org.jggug.kobo.domainlocking.*"
            }
        }
    }
}

grails.views.default.codec = "none" // none, html, base64
grails.views.gsp.encoding = "UTF-8"
