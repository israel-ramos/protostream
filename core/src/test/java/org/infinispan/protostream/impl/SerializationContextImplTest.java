package org.infinispan.protostream.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.infinispan.protostream.DescriptorParserException;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.config.Configuration;
import org.infinispan.protostream.descriptors.FileDescriptor;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author anistor@redhat.com
 * @since 2.0
 */
public class SerializationContextImplTest {

   @org.junit.Rule
   public ExpectedException exception = ExpectedException.none();

   private SerializationContextImpl createContext() {
      return (SerializationContextImpl) ProtobufUtil.newSerializationContext(new Configuration.Builder().build());
   }

   @Test
   public void testRegisterProtoFiles() throws Exception {
      SerializationContextImpl ctx = createContext();

      String file1 = "syntax = \"proto3\";\n" +
            "package p;\n" +
            "message A {\n" +
            "   optional int32 f1 = 1;\n" +
            "}";

      String file2 = "package org.infinispan;\n" +
            "import \"file1.proto\";\n" +
            "message B {\n" +
            "   required b.A ma = 1;\n" +
            "}";

      final Map<String, DescriptorParserException> failed = new HashMap<>();
      final Set<String> successful = new HashSet<>();
      FileDescriptorSource fileDescriptorSource = new FileDescriptorSource()
            .addProtoFile("file1.proto", file1)
            .addProtoFile("file2.proto", file2)
            .withProgressCallback(new FileDescriptorSource.ProgressCallback() {
               @Override
               public void handleError(String fileName, DescriptorParserException exception) {
                  failed.put(fileName, exception);
               }

               @Override
               public void handleSuccess(String fileName) {
                  successful.add(fileName);
               }
            });
      ctx.registerProtoFiles(fileDescriptorSource);

      assertEquals(1, failed.size());
      assertEquals(1, successful.size());
      DescriptorParserException exception = failed.get("file2.proto");
      assertNotNull(exception);
      assertEquals("Field type b.A not found", exception.getMessage());
      assertTrue(successful.contains("file1.proto"));

      Map<String, FileDescriptor> fileDescriptors = ctx.getFileDescriptors();

      assertEquals(3, fileDescriptors.size());

      FileDescriptor fd1 = fileDescriptors.get("file1.proto");
      assertNotNull(fd1);
      assertEquals(1, fd1.getTypes().size());
      assertTrue(fd1.getTypes().containsKey("p.A"));

      FileDescriptor fd2 = fileDescriptors.get("file2.proto");
      assertNotNull(fd2);
      assertTrue(fd2.getTypes().isEmpty());
   }

   @Test
   public void testDuplicateTypeIdInDifferentFiles() throws Exception {
      exception.expect(DescriptorParserException.class);
      exception.expectMessage("Duplicate type id 10 for type test2.M2. Already used by test1.M1");

      String file1 = "package test1;\n" +
            "/**@TypeId(10)*/\n" +
            "message M1 {\n" +
            "   optional string a = 1;\n" +
            "}";
      String file2 = "package test2;\n" +
            "/**@TypeId(10)*/\n" +
            " message M2 {\n" +
            "   optional string b = 1;\n" +
            "}";

      FileDescriptorSource source = new FileDescriptorSource();
      source.addProtoFile("test1.proto", file1);
      source.addProtoFile("test2.proto", file2);

      SerializationContextImpl ctx = createContext();
      ctx.registerProtoFiles(source);
   }

   @Test
   public void testDuplicateTypeIdInSameFile() throws Exception {
      exception.expect(DescriptorParserException.class);
      exception.expectMessage("Duplicate type id 10 for type test1.M1. Already used by test1.M2");

      String file1 = "package test1;\n" +
            "/**@TypeId(10)*/\n" +
            "message M1 {\n" +
            "   optional string a = 1;\n" +
            "}" +
            "/**@TypeId(10)*/\n" +
            "message M2 {\n" +
            "   optional string a = 1;\n" +
            "}";

      FileDescriptorSource source = new FileDescriptorSource();
      source.addProtoFile("test1.proto", file1);

      SerializationContextImpl ctx = createContext();
      ctx.registerProtoFiles(source);
   }
}
