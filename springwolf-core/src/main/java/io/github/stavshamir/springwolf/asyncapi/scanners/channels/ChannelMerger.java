// SPDX-License-Identifier: Apache-2.0
package io.github.stavshamir.springwolf.asyncapi.scanners.channels;

import io.github.stavshamir.springwolf.asyncapi.v3.model.channel.Channel;
import io.github.stavshamir.springwolf.asyncapi.v3.model.channel.ChannelObject;
import io.github.stavshamir.springwolf.asyncapi.v3.model.channel.message.Message;
import io.github.stavshamir.springwolf.asyncapi.v3.model.channel.message.MessageReference;
import io.github.stavshamir.springwolf.asyncapi.v3.model.operation.Operation;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Util to merge multiple {@link Channel}s
 */
public class ChannelMerger {

    private ChannelMerger() {}

    /**
     * Merges multiple channels by channel name
     * <p>
     * Given two channels for the same channel name, the first seen Channel is used
     * Messages within channels are merged
     *
     * @param channelEntries Ordered pairs of channel name to Channel
     * @return A map of channelName to a single Channel
     */
    public static Map<String, ChannelObject> mergeChannels(List<Map.Entry<String, ChannelObject>> channelEntries) {
        Map<String, ChannelObject> mergedChannels = new HashMap<>();

        for (Map.Entry<String, ChannelObject> entry : channelEntries) {
            if (!mergedChannels.containsKey(entry.getKey())) {
                mergedChannels.put(entry.getKey(), entry.getValue());
            } else {
                ChannelObject channel = mergeChannel(mergedChannels.get(entry.getKey()), entry.getValue());
                mergedChannels.put(entry.getKey(), channel);
            }
        }

        return mergedChannels;
    }

    /**
     * Merges multiple operations by operation name
     * <p>
     * Given two operations for the same operation name, the first seen Operation is used
     * If an operation is null, the next non-null operation is used
     * Messages within operations are merged
     *
     * @param operationEntries Ordered pairs of operation name to Operation
     * @return A map of operationName to a single Operation
     */
    public static Map<String, Operation> mergeOperations(List<Map.Entry<String, Operation>> operationEntries) {
        Map<String, Operation> mergedOperations = new HashMap<>();

        for (Map.Entry<String, Operation> entry : operationEntries) {
            if (!mergedOperations.containsKey(entry.getKey())) {
                mergedOperations.put(entry.getKey(), entry.getValue());
            } else {
                Operation operation = mergeOperation(mergedOperations.get(entry.getKey()), entry.getValue());
                mergedOperations.put(entry.getKey(), operation);
            }
        }

        return mergedOperations;
    }

    private static ChannelObject mergeChannel(ChannelObject channel, ChannelObject otherChannel) {
        ChannelObject mergedChannel = channel != null ? channel : otherChannel;

        Map<String, Message> channelMessages = channel.getMessages();
        Map<String, Message> otherChannelMessages = otherChannel.getMessages();

        Map<String, Message> mergedMessages = new HashMap<>();
        if (channelMessages != null) {
            mergedMessages.putAll(channelMessages);
        }
        if (otherChannelMessages != null) {
            otherChannelMessages.forEach(mergedMessages::putIfAbsent);
        }

        if (!mergedMessages.isEmpty()) {
            mergedChannel.setMessages(mergedMessages);
        }

        return mergedChannel;
    }

    private static Operation mergeOperation(Operation operation, Operation otherOperation) {
        Operation mergedOperation = operation != null ? operation : otherOperation;

        List<MessageReference> mergedMessages =
                mergeMessageReferences(operation.getMessages(), otherOperation.getMessages());
        if (!mergedMessages.isEmpty()) {
            mergedOperation.setMessages(mergedMessages);
        }
        return mergedOperation;
    }

    private static List<MessageReference> mergeMessageReferences(
            Collection<MessageReference> messages, Collection<MessageReference> otherMessages) {
        var messageReferences = new HashSet<MessageReference>();
        if (messages != null) {
            messageReferences.addAll(messages);
        }
        if (otherMessages != null) {
            messageReferences.addAll(otherMessages);
        }
        return messageReferences.stream().toList();
    }
}
