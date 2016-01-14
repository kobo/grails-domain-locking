package domainlocking

import grails.plugins.Plugin

class GrailsDomainLockingGrailsPlugin extends Plugin {

    def grailsVersion = "3.0 > *"
    def pluginExcludes = [
        "grails-app/views/**",
        "src/docs/**",
    ]
    def title = "Domain Locking"
    def author = "Yasuharu NAKANO"
    def authorEmail = "ynak@jggug.org"
    def license = "APACHE"
    def description = 'Provides easy ways to use an optimistic/pessimistic lock.'
    def documentation = "http://kobo.github.com/grails-domain-locking/"
    def scm = [url: "https://github.com/kobo/grails-domain-locking"]
    def issueManagement = [system: "GitHub Issues", url: "https://github.com/kobo/grails-domain-locking/issues"]

    void doWithDynamicMethods() {
        for (domainClass in grailsApplication.domainClasses) {
            domainClass.metaClass.withOptimisticLock = { Closure mainClosure ->
                OptimisticLocking.withOptimisticLock(delegate, null, [:], mainClosure)
            }
            domainClass.metaClass.withOptimisticLock << { modificationBaseVersion, Closure mainClosure ->
                OptimisticLocking.withOptimisticLock(delegate, modificationBaseVersion, [:], mainClosure)
            }
            domainClass.metaClass.withOptimisticLock << { Map params, Closure mainClosure ->
                OptimisticLocking.withOptimisticLock(delegate, null, params, mainClosure)
            }
            domainClass.metaClass.withOptimisticLock << { modificationBaseVersion, Map params, Closure mainClosure ->
                OptimisticLocking.withOptimisticLock(delegate, modificationBaseVersion, params, mainClosure)
            }

            domainClass.metaClass.static.withPessimisticLock = { Long lockingDomainId, Closure mainClosure ->
                PessimisticLocking.withPessimisticLock(delegate, lockingDomainId, mainClosure)
            }
        }
    }
}
