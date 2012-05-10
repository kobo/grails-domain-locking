<%=packageName ? "package ${packageName}\n\n" : ''%>import org.springframework.dao.DataIntegrityViolationException

class ${className}Controller {

    static allowedMethods = [create: ['GET', 'POST'], edit: ['GET', 'POST'], delete: 'POST']

    def index() {
        redirect action: 'list', params: params
    }

    def list() {
        params.max = Math.min(params.max ? params.int('max') : 10, 100)
        [${propertyName}List: ${className}.list(params), ${propertyName}Total: ${className}.count()]
    }

    def create() {
        switch (request.method) {
        case 'GET':
            [${propertyName}: new ${className}(params)]
            break
        case 'POST':
            def ${propertyName} = new ${className}(params)
            ${propertyName}.saveWithExtraFailureHandler(${propertyName}.version, params.version, {
                ${className}.withTransaction { status ->
                    if (${propertyName}.save(flush: true)) {
                        flash.message = message(code: 'default.created.message', args: [message(code: '${domainClass.propertyName}.label', default: '${className}'), ${propertyName}.id])
                        redirect action: 'show', id: ${propertyName}.id
                    }
                    else {
                        render view: 'create', model: [${propertyName}: ${propertyName}]
                    }
                }
            }, { domain ->
                render view: 'create', model: [${propertyName}: ${propertyName}]
            })
            break
        }
    }

    def show() {
        def ${propertyName} = ${className}.get(params.id)
        if (!${propertyName}) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: '${domainClass.propertyName}.label', default: '${className}'), params.id])
            redirect action: 'list'
            return
        }

        [${propertyName}: ${propertyName}]
    }

    def edit() {
        switch (request.method) {
        case 'GET':
            def ${propertyName} = ${className}.get(params.id)
            if (!${propertyName}) {
                flash.message = message(code: 'default.not.found.message', args: [message(code: '${domainClass.propertyName}.label', default: '${className}'), params.id])
                redirect action: 'list'
                return
            }

            [${propertyName}: ${propertyName}]
            break
        case 'POST':
            def ${propertyName} = ${className}.get(params.id)
            if (!${propertyName}) {
                flash.message = message(code: 'default.not.found.message', args: [message(code: '${domainClass.propertyName}.label', default: '${className}'), params.id])
                redirect action: 'list'
                return
            }

            ${propertyName}.saveWithExtraFailureHandler(${propertyName}.version, params.version, {
                ${className}.withTransaction { status ->
                    ${propertyName}.properties = params
                    if (${propertyName}.save(flush: true)) {
                        flash.message = message(code: 'default.updated.message', args: [message(code: '${domainClass.propertyName}.label', default: '${className}'), ${propertyName}.id])
                        redirect action: 'show', id: ${propertyName}.id
                    }
                    else {
                        render view: "edit", model: [${propertyName}: ${propertyName}]
                    }
                }
            }, { domain ->
                render view: "edit", model: [${propertyName}: ${propertyName}]
            })
            break
        }
    }

    def delete() {
        def ${propertyName} = ${className}.get(params.id)
        if (!${propertyName}) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: '${domainClass.propertyName}.label', default: '${className}'), params.id])
            redirect action: 'list'
            return
        }

        try {
            ${propertyName}.delete(flush: true)
            flash.message = message(code: 'default.deleted.message', args: [message(code: '${domainClass.propertyName}.label', default: '${className}'), params.id])
            redirect action: 'list'
        }
        catch (DataIntegrityViolationException e) {
            flash.message = message(code: 'default.not.deleted.message', args: [message(code: '${domainClass.propertyName}.label', default: '${className}'), params.id])
            redirect action: 'show', id: params.id
        }
    }
}
