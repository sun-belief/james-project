/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.mailbox.tools.indexer.registrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.TreeMap;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.mailbox.tools.indexer.events.ImpactingMessageEvent.FlagsMessageEvent;
import org.apache.mailbox.tools.indexer.events.ImpactingMessageEvent.MessageDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class MailboxRegistrationTest {

    public static final MailboxPath INBOX = MailboxPath.forUser("btellier@apache.org", "INBOX");
    private static final MessageUid UID = MessageUid.of(18);
    private static final int UID_VALIDITY = 45;
    private static final SimpleMailbox MAILBOX = new SimpleMailbox(INBOX, UID_VALIDITY);
    private static final int MOD_SEQ = 21;
    private static final int SIZE = 41;
    private static final Flags NEW_FLAGS = new Flags(Flags.Flag.ANSWERED);
    private MailboxRegistration mailboxRegistration;
    private EventFactory eventFactory;
    private MockMailboxSession session;

    @BeforeEach
    void setUp() {
        session = new MockMailboxSession("test");
        eventFactory = new EventFactory();
        mailboxRegistration = new MailboxRegistration(INBOX);
    }

    @Test
    void reportedEventsShouldBeInitiallyEmpty() {
        assertThat(mailboxRegistration.getImpactingEvents(UID)).isEmpty();
    }


    @Test
    void addedEventsShouldNotBeReported() {
        TreeMap<MessageUid, MessageMetaData> treeMap = new TreeMap<>();
        treeMap.put(UID, new SimpleMessageMetaData(UID, MOD_SEQ, new Flags(), SIZE, new Date(), new DefaultMessageId()));
        MailboxListener.MailboxEvent event = eventFactory.added(session, treeMap, MAILBOX, ImmutableMap.<MessageUid, MailboxMessage>of());
        mailboxRegistration.event(event);
        assertThat(mailboxRegistration.getImpactingEvents(UID)).isEmpty();
    }

    @Test
    void expungedEventsShouldBeReported() {
        TreeMap<MessageUid, MessageMetaData> treeMap = new TreeMap<>();
        treeMap.put(UID, new SimpleMessageMetaData(UID, MOD_SEQ, new Flags(), SIZE, new Date(), new DefaultMessageId()));
        MailboxListener.MailboxEvent event = eventFactory.expunged(session, treeMap, MAILBOX);
        mailboxRegistration.event(event);
        assertThat(mailboxRegistration.getImpactingEvents(UID)).containsExactly(new MessageDeletedEvent(INBOX, UID));
    }

    @Test
    void flagsEventsShouldBeReported() {
        MailboxListener.MailboxEvent event = eventFactory.flagsUpdated(session,
            Lists.newArrayList(UID),
            MAILBOX,
            Lists.newArrayList(UpdatedFlags.builder()
                .uid(UID)
                .modSeq(MOD_SEQ)
                .oldFlags(new Flags())
                .newFlags(NEW_FLAGS)
                .build()));
        mailboxRegistration.event(event);
        assertThat(mailboxRegistration.getImpactingEvents(UID)).containsExactly(new FlagsMessageEvent(INBOX, UID, NEW_FLAGS));
    }

}
