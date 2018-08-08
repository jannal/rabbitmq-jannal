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

package com.rabbitmq.client.impl.nio;

import java.nio.channels.Selector;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class SelectorHolder {

    final Selector selector;

    final Set<SocketChannelRegistration> registrations = Collections
        .newSetFromMap(new ConcurrentHashMap<SocketChannelRegistration, Boolean>());

    SelectorHolder(Selector selector) {
        this.selector = selector;
    }

    public void registerFrameHandlerState(SocketChannelFrameHandlerState state, int operations) {
        registrations.add(new SocketChannelRegistration(state, operations));
        //唤醒阻塞在selector.select()或者selector.select(timeout)上的线程
        selector.wakeup();
    }
}
