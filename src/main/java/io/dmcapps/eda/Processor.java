package io.dmcapps.eda;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.transaction.Transactional;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;


@ApplicationScoped
public class Processor {

    private static final Logger LOGGER = Logger.getLogger(Processor.class.getName());

    @Inject
    Mutiny.Session session;

    @Inject
    @Channel("products")
    Emitter<io.dmcapps.proto.catalog.Product> productEmitter;

    @Inject
    @Channel("brands")
    Emitter<io.dmcapps.proto.catalog.Brand> brandEmitter;

    @Inject
    @Channel("categories")
    Emitter<io.dmcapps.proto.catalog.Category> categoryEmitter;


    @Incoming("in-products")
    @Transactional
    public Uni<ProductEntity> createProducts(io.dmcapps.proto.catalog.Product product) {

        ProductEntity ormProduct = convertProtobufToEntity(product, ProductEntity.class);
        
        Uni<ProductEntity> uniProduct = Uni.createFrom().item(ormProduct);
        
        Uni<ProductEntity> checkProductNotExist = uniProduct
        .flatMap(p -> session.createQuery("SELECT g FROM ProductEntity g WHERE g.name = :name", ProductEntity.class)
        .setParameter("name", p.getName())
        .getSingleResult().onFailure().recoverWithNull()
        .map(pQuery -> {
            LOGGER.error(pQuery);
            if (pQuery != null) {
                LOGGER.error("Este producto ya existe");
                throw new IllegalArgumentException("Este producto ya existe");
            } else {
                return p;
            }
        })
        );
        Uni<ProductEntity> checkBrandExists = checkProductNotExist
        .flatMap(p -> session.find(BrandEntity.class, p.getBrand().getName())
        .map(brand -> {
            if (brand != null) {
                p.setBrand(brand);
                return p;
            } else {
                LOGGER.error("No existe Brand");
                throw new IllegalArgumentException("No existe Brand");
            }
        })
        );
        Uni<ProductEntity> checkCategoryExists = checkBrandExists
        .flatMap(p -> session.find(CategoryEntity.class, p.getCategory().getName())
        .map(category -> {
            if (category != null) {
                p.setCategory(category);
                return p;
            } else {
                LOGGER.error("No existe Category");
                throw new IllegalArgumentException("No existe Category");
            }
        })
        );
        Uni<ProductEntity> persistProduct = checkCategoryExists
        .onItem()
        .call(v -> session.persist(v)
            .flatMap(x -> session.flush()));
        Uni<ProductEntity> emitProductMessage = persistProduct
        .call(x -> {
            io.dmcapps.proto.catalog.Product.Builder productBuilder = io.dmcapps.proto.catalog.Product.newBuilder();
            mergeEntityToProtobuf(x, productBuilder);
            io.dmcapps.proto.catalog.Product productMessage = productBuilder
            .setStatus( io.dmcapps.proto.catalog.Product.Status.CREATED)
            .build(); 
            productEmitter.send(KafkaRecord.of(x.getId(), productMessage));
            return Uni.createFrom().item(productMessage);
        });
        return emitProductMessage
        .onFailure()
        .recoverWithNull()
        .invoke(LOGGER::info);
    }

    @Incoming("in-brands")
    @Transactional
    public Uni<BrandEntity> createBrands(io.dmcapps.proto.catalog.Brand brand) {
        
        BrandEntity ormBrand = convertProtobufToEntity(brand, BrandEntity.class);
        
        return Uni.createFrom().item(ormBrand)
        .flatMap(x -> session.find(BrandEntity.class, x.getName())
            .map(y -> {
                if (y != null) {
                    LOGGER.error("Ya existe Brand");
                    throw new IllegalArgumentException("Ya existe Brand");
                } else {
                    return x;
                }
            }))
        .onItem()
        .call(v -> session.persist(v)
            .flatMap(x -> session.flush()))
        .call(x -> {
            io.dmcapps.proto.catalog.Brand.Builder brandBuilder = io.dmcapps.proto.catalog.Brand.newBuilder();
            mergeEntityToProtobuf(x, brandBuilder);
            io.dmcapps.proto.catalog.Brand brandMessage = brandBuilder
                .setStatus( io.dmcapps.proto.catalog.Brand.Status.CREATED)
                .build(); 
            brandEmitter.send(KafkaRecord.of(x.getName(), brandMessage));
            return Uni.createFrom().item(brandMessage);
        })
        .onFailure()
        .recoverWithNull()
        .invoke(LOGGER::info);
    }
    
    @Incoming("in-categories")
    @Transactional
    public Uni<CategoryEntity> createCategories(io.dmcapps.proto.catalog.Category category) {
        
        CategoryEntity ormCategory = convertProtobufToEntity(category, CategoryEntity.class);

        return Uni.createFrom().item(ormCategory)
            .flatMap(x -> session.find(CategoryEntity.class, x.getName())
                .map(y -> {
                    if (y != null) {
                        LOGGER.error("Ya existe Category");
                        throw new IllegalArgumentException("Ya existe Category");
                    } else {
                        return x;
                    }
                }))
            .onItem()
            .call(v -> session.persist(v)
                .flatMap(x -> session.flush()))
            .call(x -> {
                io.dmcapps.proto.catalog.Category.Builder categoryBuilder = io.dmcapps.proto.catalog.Category.newBuilder();
                mergeEntityToProtobuf(x, categoryBuilder);
                io.dmcapps.proto.catalog.Category categoryMessage = categoryBuilder
                    .setStatus( io.dmcapps.proto.catalog.Category.Status.CREATED)
                    .build(); 
                categoryEmitter.send(KafkaRecord.of(x.getName(), categoryMessage));
                return Uni.createFrom().item(categoryMessage);
            })
            .onFailure()
            .recoverWithNull()
            .invoke(LOGGER::info);
    }
        
    private <T> void mergeEntityToProtobuf(T entity, Message.Builder builder) {
        Jsonb jsonb = JsonbBuilder.create();
        try {
            JsonFormat.parser().merge(jsonb.toJson(entity), builder);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        } finally {
            try {
                jsonb.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private <T> T convertJSONToEntity(String entityString, Class<T> entityClass) {
        Jsonb jsonb = JsonbBuilder.create();
        T entity = null;
        try {
            entity = jsonb.fromJson(entityString, entityClass);
        } finally {
            try {
                jsonb.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return entity;
    }

    private String convertProtobufToJSON(GeneratedMessageV3 protobufMessage) {
        String productString = null;
        try {
            productString = JsonFormat.printer().print(protobufMessage);
        } catch (InvalidProtocolBufferException e1) {
            e1.printStackTrace();
        }
        return productString;
    }

    private <T> T convertProtobufToEntity(GeneratedMessageV3 protobufMessage, Class<T> entityClass) {
        
        String json = convertProtobufToJSON(protobufMessage);
        return convertJSONToEntity(json, entityClass);
    }
    
}
