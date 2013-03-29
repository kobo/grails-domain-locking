/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jggug.kobo.domainlocking.OptimisticLocking
import org.jggug.kobo.domainlocking.PessimisticLocking

class DomainLockingGrailsPlugin {

    def version = "0.4-SNAPSHOT"
    def grailsVersion = "2.0 > *"
    def pluginExcludes = [
        "grails-app/domain/test/TestDomain.groovy",
        "src/docs/**",
        "scripts/PublishDocs.groovy",
    ]
    def title = "Domain Locking"
    def author = "Yasuharu NAKANO"
    def authorEmail = "ynak@jggug.org"
    def organization = [ name: "Japan Grails/Groovy User Group", url: "http://www.jggug.org/" ]
    def license = "APACHE"
    def description = 'Provides an easy way to use optimistic/pessimistic lock.'
    def documentation = "http://kobo.github.com/grails-domain-locking/"
    def scm = [url: "https://github.com/kobo/grails-domain-locking"]
    def issueManagement = [system: "GitHub Issues", url: "https://github.com/kobo/grails-domain-locking/issues"]

    def doWithDynamicMethods = { applicationContext ->
        for (domainClass in application.domainClasses) {
            domainClass.metaClass.withOptimisticLock = { modificationBaseVersion = null, Closure mainClosure ->
                OptimisticLocking.withOptimisticLock(delegate, modificationBaseVersion, mainClosure)
            }

            domainClass.metaClass.static.withPessimisticLock = { Long lockingDomainId, Closure mainClosure ->
                PessimisticLocking.withPessimisticLock(delegate, lockingDomainId, mainClosure)
            }
        }
    }
}
