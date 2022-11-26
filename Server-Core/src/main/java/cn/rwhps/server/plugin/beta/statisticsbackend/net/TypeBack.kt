/*
 * Copyright 2020-2022 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package cn.rwhps.server.plugin.beta.statisticsbackend.net

import cn.rwhps.server.io.packet.Packet
import cn.rwhps.server.net.core.ConnectionAgreement
import cn.rwhps.server.net.core.TypeConnect
import cn.rwhps.server.net.core.server.AbstractNetConnect
import cn.rwhps.server.util.PacketType
import cn.rwhps.server.util.ReflectionUtils

class TypeBack: TypeConnect {
    val con: NetConnectBack
    var conClass: Class<out NetConnectBack>? = null

    override val abstractNetConnect: AbstractNetConnect
        get() = con

    constructor(con: NetConnectBack) {
        this.con = con
    }
    constructor(con: Class<out NetConnectBack>) {
        this.con = ReflectionUtils.accessibleConstructor(con, ConnectionAgreement::class.java).newInstance(ConnectionAgreement())
        conClass = con
    }

    override fun getTypeConnect(connectionAgreement: ConnectionAgreement): TypeConnect {
        return TypeBack(ReflectionUtils.accessibleConstructor(conClass!!, ConnectionAgreement::class.java).newInstance(connectionAgreement))
    }

    override fun typeConnect(packet: Packet) {
        if (packet.type == PacketType.SERVER_DEBUG_RECEIVE) {
            con.AnalyticalData(packet)
        }
        con.disconnect()
    }

    override val version: String
        get() = "RW-HPS Rear End"
}