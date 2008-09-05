package com.bc.ceres.glevel.support;

import com.bc.ceres.glevel.MultiLevelSource;
import junit.framework.TestCase;

import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.FormatDescriptor;
import javax.media.jai.operator.MultiplyConstDescriptor;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;


public class GenericMultiLevelSourceTest extends TestCase {
    final double GEOPHYS_SCALING = 2.5;
    final int GEOPHYS_DATATYPE = DataBuffer.TYPE_DOUBLE;

    public void testIt() {
        final PlanarImage src = createSourceImage(256, 128);

        GenericMultiLevelSource mls = createGeophysicalSourceImage(src, 5);
        assertEquals(5, mls.getModel().getLevelCount());

        assertEquals(GEOPHYS_SCALING * 0, mls.getLevelImage(0).getData().getSampleDouble(0, 0, 0), 1e-10);
        assertEquals(GEOPHYS_SCALING * 1, mls.getLevelImage(0).getData().getSampleDouble(1, 0, 0), 1e-10);
        assertEquals(GEOPHYS_SCALING * 2, mls.getLevelImage(0).getData().getSampleDouble(0, 1, 0), 1e-10);
        assertEquals(GEOPHYS_SCALING * 3, mls.getLevelImage(0).getData().getSampleDouble(1, 1, 0), 1e-10);

        testLevelImage(mls, 0, 256, 128);
        testLevelImage(mls, 1, 128, 64);
        testLevelImage(mls, 2, 64, 32);
        testLevelImage(mls, 3, 32, 16);
        testLevelImage(mls, 4, 16, 8);
    }

    private void testLevelImage(MultiLevelSource mls, int level, int ew, int eh) {
        final RenderedImage image = mls.getLevelImage(level);
        assertSame(image, mls.getLevelImage(level));
        assertEquals(GEOPHYS_DATATYPE, image.getSampleModel().getDataType());
        assertEquals(1, image.getSampleModel().getNumBands());
        assertEquals(ew, image.getWidth());
        assertEquals(eh, image.getHeight());
    }

    private GenericMultiLevelSource createGeophysicalSourceImage(PlanarImage src, int levelCount) {
        return new GeophysicalMultiLevelSource(src, levelCount);
    }

    static PlanarImage createSourceImage(int w, int h) {
        final BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        bi.getRaster().setSample(0, 0, 0, 0);
        bi.getRaster().setSample(1, 0, 0, 1);
        bi.getRaster().setSample(0, 1, 0, 2);
        bi.getRaster().setSample(1, 1, 0, 3);
        return PlanarImage.wrapRenderedImage(bi);
    }

    private class GeophysicalMultiLevelSource extends GenericMultiLevelSource {

        public GeophysicalMultiLevelSource(PlanarImage src, int levelCount) {
            super(new DefaultMultiLevelSource(src, levelCount));
        }

        protected RenderedImage createLevelImage(RenderedImage[] sourceImages, int level) {
            final RenderedOp op = FormatDescriptor.create(PlanarImage.wrapRenderedImage(sourceImages[0]), GEOPHYS_DATATYPE, null);
            return MultiplyConstDescriptor.create(op, new double[]{GEOPHYS_SCALING}, null);
        }
    }
}
