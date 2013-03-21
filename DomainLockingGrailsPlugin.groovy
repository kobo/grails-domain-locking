import org.jggug.kobo.domainlocking.OptimisticLocking
import org.jggug.kobo.domainlocking.PessimisticLocking

class DomainLockingGrailsPlugin {

    def version = "0.2"
    def grailsVersion = "2.0 > *"
    def pluginExcludes = [
    ]
    def title = "Domain Locking"
    def author = "Yasuharu NAKANO"
    def authorEmail = "ynak@jggug.org"
    def organization = [ name: "Japan Grails/Groovy User Group", url: "http://www.jggug.org/" ]
    def license = "APACHE"
    def description = 'Provides a easy way to use optimistic/pessimistic locking.'
    def documentation = "http://kobo.github.com/grails-domain-locking/"
    def scm = [url: "https://github.com/kobo/grails-domain-locking"]
    def issueManagement = [system: "GitHub Issues", url: "https://github.com/kobo/grails-domain-locking/issues"]

    def doWithDynamicMethods = { applicationContext ->
        for (domainClass in application.domainClasses) {
            domainClass.metaClass.withOptimisticLock = { modificationBaseVersion, Closure mainClosure ->
                OptimisticLocking.withOptimisticLock(delegate, modificationBaseVersion, mainClosure)
            }

            domainClass.metaClass.static.withPessimisticLock = { Long lockingDomainId, Closure mainClosure ->
                PessimisticLocking.withPessimisticLock(delegate, lockingDomainId, mainClosure)
            }
        }
    }
}
