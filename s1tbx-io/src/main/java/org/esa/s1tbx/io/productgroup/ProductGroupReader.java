/*
 * Copyright (C) 2021 SkyWatch. https://www.skywatch.com
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.s1tbx.io.productgroup;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.s1tbx.io.productgroup.support.ProductGroupAsset;
import org.esa.s1tbx.io.productgroup.support.ProductGroupMetadataFile;
import org.esa.snap.core.dataio.AbstractProductReader;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.dataio.ProductReaderPlugIn;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.gpf.ReaderUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProductGroupReader extends AbstractProductReader {

    ProductGroupReader(ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    final List<Product> assetProducts = new ArrayList<>();

    @Override
    protected Product readProductNodesImpl() throws IOException {
        try {
            final Path inputPath = ReaderUtils.getPathFromInput(getInput());
            if(inputPath == null) {
                throw new IOException("ProductGroupReader unable to read " + getInput());
            }
            File inputFile = inputPath.toFile();

            if (inputFile.isDirectory()) {
                inputFile = ProductGroupReaderPlugIn.findMetadataFile(inputFile);
            }
            if(inputFile == null) {
                throw new IOException("ProductGroupReader unable to read " + getInput());
            }

            final File inputFolder = inputFile.getParentFile();
            final ProductGroupMetadataFile metadataFile = new ProductGroupMetadataFile();
            print("ProductGroupReader read " + inputFile);
            metadataFile.read(inputFile);

            final ProductGroupAsset[] assets = metadataFile.getAssets();
            print("ProductGroupReader found " + assets.length + " assets.");

            for (ProductGroupAsset asset : assets) {
                File assetFile = new File(asset.getPath());
                if (!assetFile.exists()) {
                    assetFile = new File(inputFolder, asset.getPath());
                }
                Product subProduct = ProductIO.readProduct(assetFile);
                assetProducts.add(subProduct);
            }

            if (!assetProducts.isEmpty()) {
                Product refProduct = assetProducts.get(0);
                final Product product = new Product(metadataFile.getProductName(), metadataFile.getProductType(),
                        refProduct.getSceneRasterWidth(), refProduct.getSceneRasterHeight());
                ProductUtils.copyProductNodes(refProduct, product);

                addSecondaryProducts(product, refProduct, assetProducts);

                product.setFileLocation(inputFile);

                return product;
            }
            throw new IOException("No ProductGroup assets found");

        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() {
        for (Product assetProduct : assetProducts) {
            assetProduct.dispose();
        }
    }

    private void addSecondaryProducts(final Product product, final Product refProduct,
                                      final List<Product> assetProducts) throws IOException {
        final MetadataElement root = product.getMetadataRoot();
        final MetadataElement slaveMetadataRoot;
        if(root.containsElement(AbstractMetadata.SLAVE_METADATA_ROOT)) {
            slaveMetadataRoot = root.getElement(AbstractMetadata.SLAVE_METADATA_ROOT);
        } else {
            slaveMetadataRoot = new MetadataElement(AbstractMetadata.SLAVE_METADATA_ROOT);
            root.addElement(slaveMetadataRoot);
        }

        for (Product assetProduct : assetProducts) {
            if (!product.isCompatibleProduct(assetProduct, 0)) {
                throw new IOException("ProductGroup asset " + assetProduct.getName() + " is incompatible");
            }

            for (Band band : assetProduct.getBands()) {
                ProductUtils.copyBand(band.getName(), assetProduct, band.getName(), product, true);
            }

            if(assetProduct != refProduct) {
                final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(assetProduct).createDeepClone();
                absRoot.setName(assetProduct.getName());
                slaveMetadataRoot.addElement(absRoot);
            }
        }
    }

    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY,
                                          int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY,
                                          Band destBand,
                                          int destOffsetX, int destOffsetY,
                                          int destWidth, int destHeight,
                                          ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {

    }

    private static void print(final String str) {
        if(ProductGroupWriterOp.DEBUG) {
            SystemUtils.LOG.info(str);
        }
    }
}
