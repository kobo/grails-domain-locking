package jp.co.ntts.grails.plugin.domainlocking

/**
 * 楽観的ロックのDSLを提供します。
 */
class OptimisticLockingDsl {

    Long baseVersion = null
    Closure mainClosure = null
    Closure onFailureHandler = null

    void baseVersion(Long baseVersionArg) {
        baseVersion = baseVersionArg
    }

    void main(Closure mainClosureArg) { // it's not the "main method"
        mainClosure = mainClosureArg
    }

    void onFailure(Closure handlerClosure) {
        onFailureHandler = handlerClosure
    }

    def methodMissing(String name, args) {
        throw new DslParseException("Unexpected DSL element is found: $name(${args})")
    }

    private void verify() {
        if (mainClosure == null) {
            throw new DslParseException("'main' is missing")
        }
    }

    static parse(Closure dslClosure) {
        def dsl = new OptimisticLockingDsl()
        dslClosure.delegate = dsl
        dslClosure.call()
        dsl.verify()
        return dsl
    }
}