/*
 * Copyright 2020-2022 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package cn.rwhps.server.plugin.beta.statisticsbackend.net

import cn.rwhps.server.core.Initialization
import cn.rwhps.server.data.global.Data
import cn.rwhps.server.io.GameInputStream
import cn.rwhps.server.io.packet.Packet
import cn.rwhps.server.net.core.ConnectionAgreement
import cn.rwhps.server.net.core.server.AbstractNetConnect
import cn.rwhps.server.plugin.beta.statisticsbackend.StatisticsBackEnd
import cn.rwhps.server.plugin.beta.statisticsbackend.StatisticsBackEnd.Companion.statisticalData
import cn.rwhps.server.util.encryption.Aes
import cn.rwhps.server.util.inline.toGson

class NetConnectBack(connectionAgreement: ConnectionAgreement) : AbstractNetConnect(connectionAgreement) {
    override val version: String
        get() = "RW-HPS Rear End"

    fun AnalyticalData(packet: Packet) {
        val dataIn = GameInputStream(packet)
        dataIn.readString()
        val uuid = dataIn.readString()
        val json = String(Aes.aesDecryptByBytes(dataIn.readStreamBytes(),"RW-HPS Statistics Data"), Data.UTF_8)
        statisticalData.put(uuid, StatisticsBackEnd.Companion.StatisticsBackEndData(Initialization.Companion.BaseDataSend::class.java.toGson(json), connectionAgreement.ipCountry))
    }

    override fun disconnect() {
        connectionAgreement.close(null)
    }
}