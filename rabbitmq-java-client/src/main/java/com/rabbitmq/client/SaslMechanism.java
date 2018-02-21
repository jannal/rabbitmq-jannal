// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client;

/**
 * Our own view of a SASL authentication mechanism, introduced to remove a
 * dependency on javax.security.sasl.
 * Sasl时一种用来扩充C/S模式验证能力的机制， sasl验证机制规范client与server之间的应答过程以及传输内容的编码方法，
 * sasl验证架构决定服务器本身如何存储客户端的身份证书以及如何核验客户端提供的密码。
 * 如果客户端能成功通过验证，服务器端就能确定用户的身份， 并借此决定用户具有怎样的权限。
 * 常见的机制:plain,login,otp, digest-md5等，这里是rabbitmq-client自己定义的一套认证机制规范
 */
public interface SaslMechanism {
    /**
     * The name of this mechanism (e.g. PLAIN)
     * @return the name
     */
    String getName();

    /**
     * Handle one round of challenge-response
     * @param challenge the challenge this round, or null on first round.
     * @param username name of user
     * @param password for username
     * @return response
     */
    LongString handleChallenge(LongString challenge, String username, String password);
}
