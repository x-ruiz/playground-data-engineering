package x.ruiz.playground.data.engineering.lakehouse.protobuf;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import org.junit.jupiter.api.Test;
import x.ruiz.playground.data.engineering.lakehouse.core.TableSpec;
import x.ruiz.playground.data.engineering.lakehouse.core.WorkloadMode;

import static org.assertj.core.api.Assertions.assertThat;

class ProtoTableSpecParserTest {

    @Test
    void shouldParseBatchTableSpec() throws Exception {
        DescriptorProto messageProto = DescriptorProto.newBuilder()
                .setName("Orders")
                .addField(FieldDescriptorProto.newBuilder().setName("order_id").setNumber(1).setType(FieldDescriptorProto.Type.TYPE_STRING).build())
                .addField(FieldDescriptorProto.newBuilder().setName("customer_id").setNumber(2).setType(FieldDescriptorProto.Type.TYPE_INT64).build())
                .addField(FieldDescriptorProto.newBuilder().setName("created_at").setNumber(3).setType(FieldDescriptorProto.Type.TYPE_INT64).build())
                .build();

        FileDescriptorProto fileProto = FileDescriptorProto.newBuilder()
                .setName("orders.proto")
                .setPackage("commerce")
                .addMessageType(messageProto)
                .build();

        FileDescriptor fileDesc = FileDescriptor.buildFrom(fileProto, new FileDescriptor[0]);
        Descriptor descriptor = fileDesc.findMessageTypeByName("Orders");

        ProtoTableSpecParser parser = new ProtoTableSpecParser();
        TableSpec spec = parser.parse(descriptor, WorkloadMode.BATCH);

        assertThat(spec.name()).isEqualTo("orders");
        assertThat(spec.namespace()).isEqualTo("commerce");
        assertThat(spec.mode()).isEqualTo(WorkloadMode.BATCH);
        assertThat(spec.properties()).containsEntry("write.target-file-size-bytes", "536870912");
        assertThat(spec.properties()).containsEntry("write.distribution-mode", "hash");
    }

    @Test
    void shouldParseStreamingTableSpec() throws Exception {
        DescriptorProto messageProto = DescriptorProto.newBuilder()
                .setName("ClickStream")
                .addField(FieldDescriptorProto.newBuilder().setName("click_id").setNumber(1).setType(FieldDescriptorProto.Type.TYPE_STRING).build())
                .addField(FieldDescriptorProto.newBuilder().setName("ts").setNumber(2).setType(FieldDescriptorProto.Type.TYPE_INT64).build())
                .build();

        FileDescriptorProto fileProto = FileDescriptorProto.newBuilder()
                .setName("clicks.proto")
                .setPackage("analytics")
                .addMessageType(messageProto)
                .build();

        FileDescriptor fileDesc = FileDescriptor.buildFrom(fileProto, new FileDescriptor[0]);
        Descriptor descriptor = fileDesc.findMessageTypeByName("ClickStream");

        ProtoTableSpecParser parser = new ProtoTableSpecParser();
        TableSpec spec = parser.parse(descriptor, WorkloadMode.STREAMING);

        assertThat(spec.name()).isEqualTo("clickstream");
        assertThat(spec.namespace()).isEqualTo("analytics");
        assertThat(spec.mode()).isEqualTo(WorkloadMode.STREAMING);
        assertThat(spec.properties()).containsEntry("write.target-file-size-bytes", "134217728");
        assertThat(spec.properties()).containsEntry("write.distribution-mode", "fanout");
        assertThat(spec.properties()).containsEntry("commit.manifest.min-count-to-merge", "4");
        assertThat(spec.partitionColumns()).contains("ts");
    }
}
