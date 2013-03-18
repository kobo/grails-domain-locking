package jp.co.ntts.grails.plugin.domainlocking

class Util {

    static Long convertToLong(number) {
        switch (number) {
            case Number: return number as Long
            case String: return number.isLong() ? number.toLong() : null
            default: return null
        }
    }

    static void shouldNotNull(argMaps) {
        argMaps.each { name, value ->
            if (value == null) throw new IllegalArgumentException("${name} should not be null.")
        }
    }
}

