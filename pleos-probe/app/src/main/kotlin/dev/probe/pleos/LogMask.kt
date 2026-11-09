package dev.probe.pleos

// 좌표는 공백 전까지, 주소는 공백이 들어가므로 줄 끝까지 가린다 (주소는 늘 마지막 항목으로 쓴다)
private val COORDINATE = Regex("""\b(lat|lon)=\S*""")
private val ADDRESS = Regex("""\baddress=.*$""")

/** 로그 값에서 좌표·주소를 가린다. 값이 왔는지는 남도록 키는 둔다. */
internal fun maskLocation(value: String): String =
    ADDRESS.replace(COORDINATE.replace(value) { "${it.groupValues[1]}=***" }, "address=***")
