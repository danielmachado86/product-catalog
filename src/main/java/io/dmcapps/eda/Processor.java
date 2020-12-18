package io.dmcapps.eda;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;



@ApplicationScoped
public class Processor {

    Logger logger = Logger.getLogger(Processor.class);

    @Inject ProductService productService;

    @Incoming("products")
    public io.dmcapps.proto.catalog.Product process(io.dmcapps.proto.catalog.Product product) throws InvalidProtocolBufferException {

        String productString = JsonFormat.printer().print(product);
        Jsonb jsonb = JsonbBuilder.create();

        Product productPanache = jsonb.fromJson(productString, Product.class);

        productService.persistProduct(productPanache);

        

        logger.info(productString);

        return product;
    }
    
}
