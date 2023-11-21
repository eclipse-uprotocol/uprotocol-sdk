/*
 * Copyright (c) 2023 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.uprotocol.rpc;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Code;
import com.google.rpc.Status;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPayloadFormat;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UPriority;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class RpcTest {

    RpcClient ReturnsNumber3 = new RpcClient() {
        @Override
        public CompletionStage<UPayload> invokeMethod(UUri topic, UPayload payload, UAttributes attributes) {
            UPayload data = UPayload.newBuilder()
                .setFormat(UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF)
                .setValue(Any.pack(Int32Value.of(3)).toByteString())
                .build();
            return CompletableFuture.completedFuture(data);
        }
    };

    RpcClient HappyPath = new RpcClient() {
        @Override
        public CompletionStage<UPayload> invokeMethod(UUri topic, UPayload payload, UAttributes attributes) {
            UPayload data = buildUPayload();
            return CompletableFuture.completedFuture(data);
        }
    };

    RpcClient WithStatusCodeInsteadOfHappyPath = new RpcClient() {
        @Override
        public CompletionStage<UPayload> invokeMethod(UUri topic, UPayload payload, UAttributes attributes) {
            Status status = Status.newBuilder().setCode(Code.INVALID_ARGUMENT_VALUE).setMessage("boom").build();
            Any any = Any.pack(status);
            UPayload data = UPayload.newBuilder()
                .setFormat(UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF)
                .setValue(any.toByteString())
                .build();
            return CompletableFuture.completedFuture(data);
        }
    };

    RpcClient WithStatusCodeHappyPath = new RpcClient() {
        @Override
        public CompletionStage<UPayload> invokeMethod(UUri topic, UPayload payload, UAttributes attributes) {
            Status status = Status.newBuilder().setCode(Code.OK_VALUE).setMessage("all good").build();
            Any any = Any.pack(status);
            UPayload data = UPayload.newBuilder()
                .setFormat(UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF)
                .setValue(any.toByteString())
                .build();
            return CompletableFuture.completedFuture(data);
        }
    };

    RpcClient ThatBarfsCrapyPayload = new RpcClient() {
        @Override
        public CompletionStage<UPayload> invokeMethod(UUri topic, UPayload payload, UAttributes attributes) {
            UPayload response = UPayload.newBuilder()
                .setFormat(UPayloadFormat.UPAYLOAD_FORMAT_RAW)
                .setValue(ByteString.copyFrom(new byte[]{0}))
                .build();
            return CompletableFuture.completedFuture(response);
        }
    };


    RpcClient ThatCompletesWithAnException = new RpcClient() {
        @Override
        public CompletionStage<UPayload> invokeMethod(UUri topic, UPayload payload, UAttributes attributes) {
            return CompletableFuture.failedFuture(new RuntimeException("Boom"));
        }

    };

    RpcClient ThatReturnsTheWrongProto = new RpcClient() {
        @Override
        public CompletionStage<UPayload> invokeMethod(UUri topic, UPayload payload, UAttributes attributes) {
            Any any = Any.pack(Int32Value.of(42));
            UPayload data = UPayload.newBuilder()
                .setFormat(UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF)
                .setValue(any.toByteString())
                .build();
            return CompletableFuture.completedFuture(data);
        }
    };


    RpcClient WithNullInPayload = new RpcClient() {
        @Override
        public CompletionStage<UPayload> invokeMethod(UUri topic, UPayload payload, UAttributes attributes) {
            return CompletableFuture.completedFuture(null);
        }
    };

    private static io.cloudevents.v1.proto.CloudEvent buildCloudEvent() {
        return io.cloudevents.v1.proto.CloudEvent.newBuilder().setSpecVersion("1.0").setId("HARTLEY IS THE BEST")
                .setSource("http://example.com").build();
    }

    private static UPayload buildUPayload() {
        Any any = Any.pack(buildCloudEvent());
        return UPayload.newBuilder()
                .setFormat(UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF)
                .setValue(any.toByteString())
                .build();
    }

    private static UUri buildTopic() {
        return LongUriSerializer.instance().deserialize("//vcu.vin/hartley/1/rpc.Raise");
    }

    private static UAttributes buildUAttributes() {
        return UAttributesBuilder.request(UPriority.UPRIORITY_CS4,
            UUri.newBuilder().setEntity(UEntity.newBuilder().setName("hartley")).build(), 1000)
                .build();

    }

    private static CompletionStage<io.cloudevents.v1.proto.CloudEvent> rpcResponse(
            CompletionStage<UPayload> invokeMethodResponse) {

        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> stubReturnValue = invokeMethodResponse.handle(
                (payload, exception) -> {
                    Any any;
                    try {
                        any = Any.parseFrom(payload.getValue());
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }

                    // invoke method had some unexpected problem.
                    if (exception != null) {
                        throw new RuntimeException(exception.getMessage(), exception);
                    }

                    // test to see if we have expected type
                    if (any.is(io.cloudevents.v1.proto.CloudEvent.class)) {
                        try {
                            return any.unpack(io.cloudevents.v1.proto.CloudEvent.class);
                        } catch (InvalidProtocolBufferException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    }

                    // this will be called only if expected return type is not status, but status was returned to
                    // indicate a problem.
                    if (any.is(Status.class)) {
                        try {
                            Status status = any.unpack(Status.class);
                            throw new RuntimeException(String.format("Error returned, status code: [%s], message: [%s]",
                                    Code.forNumber(status.getCode()), status.getMessage()));
                        } catch (InvalidProtocolBufferException e) {
                            throw new RuntimeException(
                                    String.format("%s [%s]", e.getMessage(), "com.google.grpc.Status.class"), e);
                        }
                    }

                    throw new RuntimeException(String.format("Unknown payload type [%s]", any.getTypeUrl()));

                });

        return stubReturnValue;
    }

    @Test
    void test_compose_happy_path() {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<Int32Value>> rpcResponse = RpcMapper.mapResponseToResult(
                        ReturnsNumber3.invokeMethod(buildTopic(), payload, buildUAttributes()), Int32Value.class)
                .thenApply(ur -> ur.map(i -> Int32Value.of(i.getValue() + 5))).exceptionally(exception -> {
                    System.out.println("in exceptionally");
                    return RpcResult.failure("boom", exception);
                });
        assertFalse(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        final CompletionStage<Void> test = rpcResponse.thenAccept(RpcResult -> {
            assertTrue(RpcResult.isSuccess());
            assertEquals(Int32Value.of(8), RpcResult.successValue());
        });
        assertFalse(test.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void test_compose_that_returns_status() throws ExecutionException, InterruptedException {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<Int32Value>> rpcResponse = RpcMapper.mapResponseToResult(
                        WithStatusCodeInsteadOfHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
                        Int32Value.class).thenApply(ur -> ur.map(i -> Int32Value.of(i.getValue() + 5)))
                .exceptionally(exception -> {
                    System.out.println("in exceptionally");
                    return RpcResult.failure("boom", exception);
                });
        assertFalse(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        final CompletionStage<Void> test = rpcResponse.thenAccept(RpcResult -> {
            assertTrue(RpcResult.isFailure());
            assertEquals(Code.INVALID_ARGUMENT_VALUE, RpcResult.failureValue().getCode());
            assertEquals("boom", RpcResult.failureValue().getMessage());
        });
        assertFalse(test.toCompletableFuture().isCompletedExceptionally());
        assertEquals(rpcResponse.toCompletableFuture().get().failureValue().getCode(), Code.INVALID_ARGUMENT_VALUE);
        assertFalse(test.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void test_compose_with_failure() {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<Int32Value>> rpcResponse = RpcMapper.mapResponseToResult(
                        ThatCompletesWithAnException.invokeMethod(buildTopic(), payload, buildUAttributes()),
                        Int32Value.class)
                .thenApply(ur -> ur.map(i -> Int32Value.of(i.getValue() + 5)));
        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(), "java.lang.RuntimeException: Boom");
    }

    @Test
    void test_compose_with_failure_transform_Exception() {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<Int32Value>> rpcResponse = RpcMapper.mapResponseToResult(
                        ThatCompletesWithAnException.invokeMethod(buildTopic(), payload, buildUAttributes()),
                        Int32Value.class)
                .thenApply(ur -> ur.map(i -> Int32Value.of(i.getValue() + 5))).exceptionally(exception -> {
                    System.out.println("in exceptionally");
                    return RpcResult.failure("boom", exception);
                });
        assertFalse(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        final CompletionStage<Void> test = rpcResponse.thenAccept(RpcResult -> {
            assertTrue(RpcResult.isFailure());
            assertEquals(Code.UNKNOWN_VALUE, RpcResult.failureValue().getCode());
            assertEquals("boom", RpcResult.failureValue().getMessage());
        });
        assertFalse(test.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void test_success_invoke_method_happy_flow_using_mapResponseToRpcResponse() {
        UPayload payload = buildUPayload();

        final CompletionStage<RpcResult<io.cloudevents.v1.proto.CloudEvent>> rpcResponse =
                RpcMapper.mapResponseToResult(
                HappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertFalse(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        final CompletionStage<Void> test = rpcResponse.thenAccept(RpcResult -> {
            assertTrue(RpcResult.isSuccess());
            assertEquals(buildCloudEvent(), RpcResult.successValue());
        });
        assertFalse(test.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void test_fail_invoke_method_when_invoke_method_returns_a_status_using_mapResponseToRpcResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<io.cloudevents.v1.proto.CloudEvent>> rpcResponse =
                RpcMapper.mapResponseToResult(
                WithStatusCodeInsteadOfHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertFalse(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        final CompletionStage<Void> test = rpcResponse.thenAccept(RpcResult -> {
            assertTrue(RpcResult.isFailure());
            assertEquals(Code.INVALID_ARGUMENT.getNumber(), RpcResult.failureValue().getCode());
            assertEquals("boom", RpcResult.failureValue().getMessage());
        });
        assertFalse(test.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void test_fail_invoke_method_when_invoke_method_threw_an_exception_using_mapResponseToRpcResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<io.cloudevents.v1.proto.CloudEvent>> rpcResponse =
                RpcMapper.mapResponseToResult(
                ThatCompletesWithAnException.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(), "java.lang.RuntimeException: Boom");
    }

    @Test
    void test_fail_invoke_method_when_invoke_method_returns_a_bad_proto_using_mapResponseToRpcResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<io.cloudevents.v1.proto.CloudEvent>> rpcResponse =
                RpcMapper.mapResponseToResult(
                ThatReturnsTheWrongProto.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(),
                "java.lang.RuntimeException: Unknown payload type [type.googleapis.com/google.protobuf.Int32Value]. " +
                        "Expected [io.cloudevents.v1.proto.CloudEvent]");
    }

    @Test
    void test_success_invoke_method_happy_flow_using_mapResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> rpcResponse = RpcMapper.mapResponse(
                HappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertFalse(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        final CompletionStage<Void> test = rpcResponse.thenAccept(
                cloudEvent -> assertEquals(buildCloudEvent(), cloudEvent));
        assertFalse(test.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void test_fail_invoke_method_when_invoke_method_returns_a_status_using_mapResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> rpcResponse = RpcMapper.mapResponse(
                WithStatusCodeInsteadOfHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());

        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(),
                "java.lang.RuntimeException: Unknown payload type [type.googleapis.com/google.rpc.Status]. Expected " +
                        "[io.cloudevents.v1.proto.CloudEvent]");
    }

    @Test
    void test_fail_invoke_method_when_invoke_method_threw_an_exception_using_mapResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> rpcResponse = RpcMapper.mapResponse(
                ThatCompletesWithAnException.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(), "java.lang.RuntimeException: Boom");
    }

    @Test
    void test_fail_invoke_method_when_invoke_method_returns_a_bad_proto_using_mapResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> rpcResponse = RpcMapper.mapResponse(
                ThatReturnsTheWrongProto.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(),
                "java.lang.RuntimeException: Unknown payload type [type.googleapis.com/google.protobuf.Int32Value]. " +
                        "Expected [io.cloudevents.v1.proto.CloudEvent]");
    }

    @Test
    void test_success_invoke_method_happy_flow() {
        //Stub code
        UPayload data = buildUPayload();
        final CompletionStage<UPayload> rpcResponse = HappyPath.invokeMethod(buildTopic(), data, buildUAttributes());

        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> stubReturnValue = rpcResponse.handle(
                (payload, exception) -> {
                    Any any;
                    assertTrue(true);
                    assertFalse(true);

                    try {
                        any = Any.parseFrom(payload.getValue());
                        // happy flow, no exception
                        assertNull(exception);

                        // check the payload is not google.rpc.Status
                        assertFalse(any.is(Status.class));

                        // check the payload is the cloud event we build
                        assertTrue(any.is(io.cloudevents.v1.proto.CloudEvent.class));

                        return any.unpack(io.cloudevents.v1.proto.CloudEvent.class);
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });

        stubReturnValue.thenAccept(cloudEvent -> assertEquals(buildUPayload(), cloudEvent));

    }

    @Test
    void test_fail_invoke_method_when_invoke_method_returns_a_status() {
        //Stub code
        UPayload data = buildUPayload();
        final CompletionStage<UPayload> rpcResponse = WithStatusCodeInsteadOfHappyPath.invokeMethod(buildTopic(),
                data, buildUAttributes());

        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> stubReturnValue = rpcResponse.handle(
                (payload, exception) -> {
                    try {
                        Any any = Any.parseFrom(payload.getValue());
                        // happy flow, no exception
                        assertNull(exception);

                        // check the payload not google.rpc.Status
                        assertTrue(any.is(Status.class));

                        // check the payload is not the type we expected
                        assertFalse(any.is(io.cloudevents.v1.proto.CloudEvent.class));

                        // we know it is a Status - so let's unpack it

                        Status status = any.unpack(Status.class);
                        throw new RuntimeException(String.format("Error returned, status code: [%s], message: [%s]",
                                Code.forNumber(status.getCode()), status.getMessage()));
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });

        assertTrue(stubReturnValue.toCompletableFuture().isCompletedExceptionally());

        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, stubReturnValue.toCompletableFuture()::get);
        assertEquals(exception.getMessage(),
                "java.lang.RuntimeException: Error returned, status code: [INVALID_ARGUMENT], message: [boom]");

    }

    @Test
    void test_fail_invoke_method_when_invoke_method_threw_an_exception() {
        //Stub code
        UPayload data = buildUPayload();
        final CompletionStage<UPayload> rpcResponse = ThatCompletesWithAnException.invokeMethod(buildTopic(), data,
                buildUAttributes());

        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> stubReturnValue = rpcResponse.handle(
                (payload, exception) -> {
                    // exception was thrown
                    assertNotNull(exception);

                    assertNull(payload);

                    throw new RuntimeException(exception.getMessage(), exception);

                });

        assertTrue(stubReturnValue.toCompletableFuture().isCompletedExceptionally());

        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, stubReturnValue.toCompletableFuture()::get);
        assertEquals(exception.getMessage(), "java.lang.RuntimeException: Boom");

    }

    @Test
    void test_fail_invoke_method_when_invoke_method_returns_a_bad_proto() {
        //Stub code
        UPayload data = buildUPayload();
        final CompletionStage<UPayload> rpcResponse = ThatReturnsTheWrongProto.invokeMethod(buildTopic(), data,
                buildUAttributes());

        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> stubReturnValue = rpcResponse.handle(
                (payload, exception) -> {
                    try {
                        Any any = Any.parseFrom(payload.getValue());
                        // happy flow, no exception
                        assertNull(exception);

                        // check the payload is not google.rpc.Status
                        assertFalse(any.is(Status.class));

                        // check the payload is the cloud event we build
                        assertFalse(any.is(io.cloudevents.v1.proto.CloudEvent.class));

                        return any.unpack(io.cloudevents.v1.proto.CloudEvent.class);
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(
                                String.format("%s [%s]", e.getMessage(), "io.cloudevents.v1.proto.CloudEvent.class"),
                                e);
                    }
                });

        assertTrue(stubReturnValue.toCompletableFuture().isCompletedExceptionally());

        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, stubReturnValue.toCompletableFuture()::get);
        assertEquals(exception.getMessage(),
                "java.lang.RuntimeException: Type of the Any message does not match the given class. [io.cloudevents" +
                        ".v1.proto.CloudEvent.class]");

    }

    @Test
    @DisplayName("Invoke method that returns successfully with null in the payload")
    void test_success_invoke_method_that_has_null_payload_mapResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<io.cloudevents.v1.proto.CloudEvent> rpcResponse = RpcMapper.mapResponse(
                WithNullInPayload.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(),
                "java.lang.RuntimeException: Server returned a null payload. Expected io.cloudevents.v1.proto" +
                        ".CloudEvent");

    }

    @Test
    @DisplayName("Invoke method that returns successfully with null in the payload, mapResponseToResult")
    void test_success_invoke_method_that_has_null_payload_mapResponseToResultToRpcResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<io.cloudevents.v1.proto.CloudEvent>> rpcResponse =
                RpcMapper.mapResponseToResult(
                WithNullInPayload.invokeMethod(buildTopic(), payload, buildUAttributes()),
                io.cloudevents.v1.proto.CloudEvent.class);

        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(),
                "java.lang.RuntimeException: Server returned a null payload. Expected io.cloudevents.v1.proto" +
                        ".CloudEvent");

    }

    @Test
    @DisplayName("Invoke method that expects a Status payload and returns successfully with OK Status in the payload")
    void test_success_invoke_method_happy_flow_that_returns_status_using_mapResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<Status> rpcResponse = RpcMapper.mapResponse(
                WithStatusCodeHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()), Status.class);

        assertFalse(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        final CompletionStage<Void> test = rpcResponse.thenAccept(status -> {
            assertEquals(Code.OK.getNumber(), status.getCode());
            assertEquals("all good", status.getMessage());
        });
        assertFalse(test.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    @DisplayName("Invoke method that expects a Status payload and returns successfully with OK Status in the payload," +
            " mapResponseToResult")
    void test_success_invoke_method_happy_flow_that_returns_status_using_mapResponseToResultToRpcResponse() {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<Status>> rpcResponse = RpcMapper.mapResponseToResult(
                WithStatusCodeHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()), Status.class);

        assertFalse(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        final CompletionStage<Void> test = rpcResponse.thenAccept(RpcResult -> {
            assertTrue(RpcResult.isSuccess());
            assertEquals(Code.OK.getNumber(), RpcResult.successValue().getCode());
            assertEquals("all good", RpcResult.successValue().getMessage());
        });
        assertFalse(test.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void test_unpack_payload_failed() {
        Any payload = Any.pack(Int32Value.of(3));
        Exception exception = assertThrows(RuntimeException.class,
                () -> RpcMapper.unpackPayload(payload, Status.class));
        assertEquals(exception.getMessage(),
                "Type of the Any message does not match the given class. [com.google.rpc.Status]");
    }

    @Test
    @DisplayName("test invalid payload that is not of type any")
    void test_invalid_payload_that_is_not_type_any() {
        UPayload payload = buildUPayload();
        final CompletionStage<Status> rpcResponse = RpcMapper.mapResponse(
                ThatBarfsCrapyPayload.invokeMethod(buildTopic(), payload, buildUAttributes()), Status.class);

        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(),
                "java.lang.RuntimeException: Protocol message contained an invalid tag (zero). [com.google.rpc" +
                        ".Status]");
        ;
    }

    @Test
    @DisplayName("test invalid payload that is not of type any")
    void test_invalid_payload_that_is_not_type_any_map_to_result() {
        UPayload payload = buildUPayload();
        final CompletionStage<RpcResult<Status>> rpcResponse = RpcMapper.mapResponseToResult(
                ThatBarfsCrapyPayload.invokeMethod(buildTopic(), payload, buildUAttributes()), Status.class);

        assertTrue(rpcResponse.toCompletableFuture().isCompletedExceptionally());
        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, rpcResponse.toCompletableFuture()::get);
        assertEquals(exception.getMessage(),
                "java.lang.RuntimeException: Protocol message contained an invalid tag (zero). [com.google.rpc" +
                        ".Status]");
        ;
    }

    @Test
    void what_the_stub_looks_like() throws InterruptedException {

        RpcClient client = new RpcClient() {
            @Override
            public CompletionStage<UPayload> invokeMethod(UUri topic, UPayload payload, UAttributes attributes) {
                return CompletableFuture.completedFuture(UPayload.getDefaultInstance());
            }
        };

        //Stub code

        UPayload payload = buildUPayload();
        final CompletionStage<UPayload> invokeMethodResponse = client.invokeMethod(buildTopic(), payload,
                buildUAttributes());

        CompletionStage<io.cloudevents.v1.proto.CloudEvent> stubReturnValue = rpcResponse(invokeMethodResponse);
        assertFalse(stubReturnValue.toCompletableFuture().isCancelled());

    }

}