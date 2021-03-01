package matt.media.player

class Version private constructor(): Comparable<Version> {
    var major = 0
        private set
    var minor = 0
        private set
    var patch = 0
        private set
    
    constructor(major: Int, minor: Int, patch: Int): this() {
        this.major = major
        this.minor = minor
        this.patch = patch
    }
    
    constructor(version: String): this() {
        val split = version.split('.')
        if(split.size > 3)
            throw IllegalArgumentException("version string must only split into at most 3 parts")
        major = split[0].toInt()
        if(split.size > 1)
            minor = split[1].toInt()
        if(split.size > 2)
            patch = split[2].toInt()
    }
    
    override fun compareTo(other: Version): Int {
        val majorDiff = major - other.major
        if(majorDiff != 0)
            return majorDiff
        val minorDiff = minor - other.minor
        if(minorDiff != 0)
            return minorDiff
        return patch - other.patch
    }
    
    override fun equals(other: Any?): Boolean {
        if(other !is Version)
            return false
        return major == other.major && minor == other.minor && patch == other.patch
    }
    
    override fun hashCode(): Int {
        return major xor minor xor patch
    }
    
    override fun toString(): String {
        return "$major.$minor.$patch"
    }
}