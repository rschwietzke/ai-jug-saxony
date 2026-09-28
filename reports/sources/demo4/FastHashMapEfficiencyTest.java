package org.jugsaxony.demo4;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class FastHashMapEfficiencyTest {

    @Test
    public void testClassLayout() {
        String layout = ClassLayout.parseClass(FastHashMap.class).toPrintable();
        System.out.println("FastHashMap Class Layout:\n" + layout);
        assertThat(layout).isNotEmpty();
    }

    @Test
    public void testMemoryFootprintEmpty() {
        FastHashMap<String, String> fastMap = new FastHashMap<>();
        Map<String, String> javaMap = new HashMap<>();

        long fastMapSize = GraphLayout.parseInstance(fastMap).totalSize();
        long javaMapSize = GraphLayout.parseInstance(javaMap).totalSize();

        System.out.printf("Empty - FastHashMap: %d bytes, HashMap: %d bytes%n", fastMapSize, javaMapSize);
        assertThat(fastMapSize).isGreaterThan(0);
    }

    @Test
    public void testMemoryFootprintPopulated() {
        int[] sizes = { 0, 10, 100, 1_000, 10_000, 100_000 };

        for (int n : sizes) {
            FastHashMap<Integer, Integer> fastMap = new FastHashMap<>();
            Map<Integer, Integer> javaMap = new HashMap<>();

            for (int i = 0; i < n; i++) {
                fastMap.put(i, i);
                javaMap.put(i, i);
            }

            long fastMapTotal = GraphLayout.parseInstance(fastMap).totalSize();
            long javaMapTotal = GraphLayout.parseInstance(javaMap).totalSize();

            double fastPerEntry = (double) fastMapTotal / n;
            double javaPerEntry = (double) javaMapTotal / n;

            System.out.printf("N=%d -> FastHashMap: %d bytes (%.1f B/entry), HashMap: %d bytes (%.1f B/entry)%n",
                    n, fastMapTotal, fastPerEntry, javaMapTotal, javaPerEntry);

            assertThat(fastMapTotal).isGreaterThan(0);
        }
    }
}
