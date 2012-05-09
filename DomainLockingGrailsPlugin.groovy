import jp.co.ntts.grails.plugin.domainlocking.OptimisticLockingUtil

class DomainLockingGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.0 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Grails Domain Locking Plugin" // Headline display name of the plugin
    def author = "NTT Software"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/grails-domain-locking"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
//    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.grails-plugins.codehaus.org/browse/grails-plugins/" ]

     def doWithDynamicMethods = { applicationContext ->
         for (domainClass in application.domainClasses) {
             // OptimisticLockingUtil
             // TODO to extract the enhancement into an individual class
             domainClass.metaClass.saveWithDefaultFailureHandler = { persistentVersion, modificationBaseVersion, Closure updateClosure ->
                 OptimisticLockingUtil.withDefaultFailureHandler(delegate, persistentVersion, modificationBaseVersion, updateClosure)
             }
             domainClass.metaClass.saveWithFailureHandler = { persistentVersion, modificationBaseVersion, Closure updateClosure, Closure failureHandler ->
                 OptimisticLockingUtil.withFailureHandler(delegate, persistentVersion, modificationBaseVersion, updateClosure, failureHandler)
             }
             domainClass.metaClass.saveWithExtraFailureHandler = { persistentVersion, modificationBaseVersion, Closure updateClosure, Closure extraFailureHandler ->
                 OptimisticLockingUtil.withExtraFailureHandler(delegate, persistentVersion, modificationBaseVersion, updateClosure, extraFailureHandler)
             }
             domainClass.metaClass.tryUpdate = { persistentVersion, modificationBaseVersion, Closure updateClosure, Closure failureHandler ->
                 OptimisticLockingUtil.tryUpdate(delegate, persistentVersion, modificationBaseVersion, updateClosure, failureHandler)
             }
         }
    }
}
