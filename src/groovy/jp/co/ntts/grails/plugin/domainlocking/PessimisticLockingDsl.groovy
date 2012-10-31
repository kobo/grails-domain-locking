package jp.co.ntts.grails.plugin.domainlocking

/**
 * 悲観的ロックのDSLを提供します。
 */
class PessimisticLockingDsl {

    private static final DEFAULT_ON_NOT_FOUND_CLOSURE = { long id -> /* do nothing */ }

    Closure mainClosure = null
    Closure onNotFoundClosure = DEFAULT_ON_NOT_FOUND_CLOSURE

    void main(Closure mainClosureArg) { // it's not the "main method"
        mainClosure = mainClosureArg
    }

    void onNotFound(Closure onNotFoundClosureArg) {
        onNotFoundClosure = onNotFoundClosureArg
    }

    def methodMissing(String name, args) {
        throw new DslParseException("Unexpected DSL element is found: $name(${args})")
    }

    private void verify() {
        if (mainClosure == null) {
            throw new DslParseException("'main' is missing")
        }
        if (onNotFoundClosure == null) {
            throw new DslParseException("'onNotFound' is missing")
        }
    }

    static parse(Closure dslClosure) {
        def dsl = new PessimisticLockingDsl()
        dslClosure.delegate = dsl
        dslClosure.call()
        dsl.verify()
        return dsl
    }
}