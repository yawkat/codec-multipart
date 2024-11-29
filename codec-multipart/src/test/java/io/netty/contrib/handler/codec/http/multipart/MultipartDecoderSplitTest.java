/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.contrib.handler.codec.http.multipart;

import com.code_intelligence.jazzer.junit.FuzzTest;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.CompositeBuffer;
import io.netty5.buffer.DefaultBufferAllocators;
import org.junit.jupiter.api.Assertions;

/**
 * This fuzz test verifies that the parsed stream of a single buffer is the same as that of a buffer arriving in
 * multiple chunks.
 */
public class MultipartDecoderSplitTest extends AbstractFuzzTest {
    public static void main(String[] args) throws Throwable {
        minimize(MultipartDecoderSplitTest.class, "minimized-from-c9c7d734aefa8ce74150aafe9152fb9641da5f93");
    }

    @SuppressWarnings("unused")
    public static void fuzzerTestOneInput(byte[] bytes) {
        new MultipartDecoderSplitTest().compare(bytes);
    }

    @MultipartFuzzTest
    @FuzzTest(maxDuration = "2h")
    public void compare(byte[] bytes) {
        try (PostBodyDecoder splitDecoder = PostBodyDecoder.builder().forMultipartBoundary(BOUNDARY);
             PostBodyDecoder jointDecoder = PostBodyDecoder.builder().forMultipartBoundary(BOUNDARY)) {

            try (Buffer jointBuffer = DefaultBufferAllocators.preferredAllocator().allocate(bytes.length);
                 Buffer splitBuffer = DefaultBufferAllocators.preferredAllocator().copyOf(bytes)) {
                while (true) {
                    Buffer piece = readUntilSeparator(splitBuffer);
                    if (piece == null) {
                        break;
                    }
                    jointBuffer.writeBytes(piece);
                }
                jointBuffer.writeBytes(splitBuffer);
                jointDecoder.add(jointBuffer.send());
            }

            CompositeBuffer splitContent = null;
            try (Buffer splitBuffer = DefaultBufferAllocators.preferredAllocator().copyOf(bytes)) {

                boolean last = false;
                while (!last) {
                    Buffer piece = readUntilSeparator(splitBuffer);
                    if (piece == null) {
                        last = true;
                        piece = splitBuffer;
                    }
                    splitDecoder.add(piece.send());

                    while (true) {
                        PostBodyDecoder.Event event;
                        try {
                            event = splitDecoder.next();
                        } catch (HttpPostRequestDecoder.ErrorDataDecoderException splitE) {
                            try {
                                jointDecoder.next();
                                Assertions.fail("Joint decoder should also fail", splitE);
                            } catch (HttpPostRequestDecoder.ErrorDataDecoderException jointE) {
                                Assertions.assertEquals(jointE.getMessage(), splitE.getMessage());
                            }
                            return;
                        }
                        if (event == null) {
                            break;
                        }
                        if (splitContent == null) {
                            Assertions.assertEquals(jointDecoder.next(), event);
                            if (event == PostBodyDecoder.Event.HEADER) {
                                Assertions.assertEquals(jointDecoder.headerName(), splitDecoder.headerName());
                                Assertions.assertEquals(jointDecoder.headerValue(), splitDecoder.headerValue());
                            } else if (event == PostBodyDecoder.Event.HEADERS_COMPLETE) {
                                splitContent = DefaultBufferAllocators.preferredAllocator().compose();
                            }
                        } else {
                            if (event == PostBodyDecoder.Event.CONTENT) {
                                splitContent.extendWith(splitDecoder.decodedContent());
                            } else {
                                Assertions.assertEquals(PostBodyDecoder.Event.FIELD_COMPLETE, event);
                                PostBodyDecoder.Event jointEvent = jointDecoder.next();
                                Buffer jointContent;
                                if (jointEvent == PostBodyDecoder.Event.CONTENT) {
                                    jointContent = jointDecoder.decodedContent().receive();
                                    Assertions.assertEquals(PostBodyDecoder.Event.FIELD_COMPLETE, jointDecoder.next());
                                } else {
                                    Assertions.assertEquals(PostBodyDecoder.Event.FIELD_COMPLETE, jointEvent);
                                    jointContent = DefaultBufferAllocators.preferredAllocator().allocate(0);
                                }
                                try (jointContent) {
                                    Assertions.assertEquals(jointContent, splitContent);
                                }
                                splitContent.close();
                                splitContent = null;
                            }
                        }
                    }
                }
            } finally {
                if (splitContent != null) {
                    splitContent.close();
                }
            }
        }
    }
}
