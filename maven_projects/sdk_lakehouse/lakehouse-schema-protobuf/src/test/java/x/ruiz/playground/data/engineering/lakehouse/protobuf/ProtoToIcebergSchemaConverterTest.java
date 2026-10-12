package x.ruiz.playground.data.engineering.lakehouse.protobuf;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Timestamp;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtoToIcebergSchemaConverterTest {

    private ProtoToIcebergSchemaConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ProtoToIcebergSchemaConverter();
    }

    @Test
    void shouldConvertPrimitiveFieldsAndPreserveFieldNumbers() throws Exception {
        DescriptorProto messageProto = DescriptorProto.newBuilder()
                .setName("TransactionEvent")
                .addField(FieldDescriptorProto.newBuilder().setName("tx_id").setNumber(1).setType(FieldDescriptorProto.Type.TYPE_STRING).build())
                .addField(FieldDescriptorProto.newBuilder().setName("account_id").setNumber(2).setType(FieldDescriptorProto.Type.TYPE_INT64).build())
                .addField(FieldDescriptorProto.newBuilder().setName("amount").setNumber(3).setType(FieldDescriptorProto.Type.TYPE_DOUBLE).build())
                .addField(FieldDescriptorProto.newBuilder().setName("is_flagged").setNumber(4).setType(FieldDescriptorProto.Type.TYPE_BOOL).build())
                .build();

        FileDescriptorProto fileProto = FileDescriptorProto.newBuilder()
                .setName("transaction.proto")
                .setPackage("fintech.banking")
                .addMessageType(messageProto)
                .build();

        FileDescriptor fileDesc = FileDescriptor.buildFrom(fileProto, new FileDescriptor[0]);
        Descriptor descriptor = fileDesc.findMessageTypeByName("TransactionEvent");

        Schema schema = converter.toIcebergSchema(descriptor);

        assertThat(schema.columns()).hasSize(4);

        // Verify permanent proto tag alignment with Iceberg field-ids
        assertThat(schema.findField("tx_id").fieldId()).isEqualTo(1);
        assertThat(schema.findField("tx_id").type()).isEqualTo(Types.StringType.get());

        assertThat(schema.findField("account_id").fieldId()).isEqualTo(2);
        assertThat(schema.findField("account_id").type()).isEqualTo(Types.LongType.get());

        assertThat(schema.findField("amount").fieldId()).isEqualTo(3);
        assertThat(schema.findField("amount").type()).isEqualTo(Types.DoubleType.get());

        assertThat(schema.findField("is_flagged").fieldId()).isEqualTo(4);
        assertThat(schema.findField("is_flagged").type()).isEqualTo(Types.BooleanType.get());
    }

    @Test
    void shouldConvertWellKnownTimestamp() {
        Descriptor timestampDesc = Timestamp.getDescriptor();
        Schema schema = converter.toIcebergSchema(timestampDesc);

        assertThat(schema.columns()).hasSize(2);
        assertThat(schema.findField("seconds").fieldId()).isEqualTo(1);
        assertThat(schema.findField("seconds").type()).isEqualTo(Types.LongType.get());
        assertThat(schema.findField("nanos").fieldId()).isEqualTo(2);
        assertThat(schema.findField("nanos").type()).isEqualTo(Types.IntegerType.get());
    }

    @Test
    void shouldConvertRepeatedFieldsToListType() throws Exception {
        DescriptorProto messageProto = DescriptorProto.newBuilder()
                .setName("UserWithTags")
                .addField(FieldDescriptorProto.newBuilder().setName("user_id").setNumber(1).setType(FieldDescriptorProto.Type.TYPE_INT64).build())
                .addField(FieldDescriptorProto.newBuilder().setName("tags").setNumber(5).setType(FieldDescriptorProto.Type.TYPE_STRING)
                        .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED).build())
                .build();

        FileDescriptorProto fileProto = FileDescriptorProto.newBuilder()
                .setName("user.proto")
                .addMessageType(messageProto)
                .build();

        FileDescriptor fileDesc = FileDescriptor.buildFrom(fileProto, new FileDescriptor[0]);
        Descriptor descriptor = fileDesc.findMessageTypeByName("UserWithTags");

        Schema schema = converter.toIcebergSchema(descriptor);

        assertThat(schema.findField("tags").type().isListType()).isTrue();
        Types.ListType listType = schema.findField("tags").type().asListType();
        assertThat(listType.elementType()).isEqualTo(Types.StringType.get());
    }
}
