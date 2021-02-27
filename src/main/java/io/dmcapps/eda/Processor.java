package io.dmcapps.eda;

import java.time.Duration;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import io.dmcapps.proto.catalog.Transaction.Status;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.annotations.Merge;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;


public class Processor {

    private static final Logger LOGGER = Logger.getLogger(Processor.class.getName());
    private static final String PRODUCT_EXIST = "This product already exist";
    private static final String BRAND_NOT_EXIST = "This brand doesn't exist";
    private static final String BRAND_ALREADY_EXIST = "This brand already exist";
    private static final String CATEGORY_NOT_EXIST = "This category doesn't exist";
    private static final String CATEGORY_ALREADY_EXIST = "This category already exist";

    @Inject
    Mutiny.Session session;

    @Inject
    @Channel("transactions")
    Emitter<io.dmcapps.proto.catalog.Transaction> transactionEmitter;

    @Inject
    @Channel("products")
    Emitter<io.dmcapps.proto.catalog.Product> productEmitter;

    @Inject
    @Channel("brands")
    Emitter<io.dmcapps.proto.catalog.Brand> brandEmitter;

    @Inject
    @Channel("categories")
    Emitter<io.dmcapps.proto.catalog.Category> categoryEmitter;


    @Incoming("product-catalog-transactions")
    // @Outgoing("transactions")
    public Uni<io.dmcapps.proto.catalog.Transaction> processTransactions(io.dmcapps.proto.catalog.Transaction transaction) {
        
        String transactionString  = convertProtobufToJSON(transaction);
        TransactionEntity transactionEntity = convertJSONToEntity(transactionString, TransactionEntity.class);

        io.dmcapps.proto.catalog.Transaction.Builder transactionBuilder = io.dmcapps.proto.catalog.Transaction.newBuilder(transaction);
        
        Uni<io.dmcapps.proto.catalog.Transaction.Builder> process = Uni.createFrom().item(transactionBuilder);

        if(transaction.getEntityName().equals("Product")){
            process = productTransaction(transactionBuilder, transactionEntity);
        }
        if(transaction.getEntityName().equals("Brand")){
            process = brandTransaction(transactionBuilder, transactionEntity);
        }
        if(transaction.getEntityName().equals("Category")){
            process = categoryTransaction(transactionBuilder, transactionEntity);
        }
        
        return process
            .onItem().transform(tx -> tx.build())
            .onItem().invoke(tx -> LOGGER.info(tx.getStatus() + "\t" + tx.getErrorList()));
        
    }

    private Uni<io.dmcapps.proto.catalog.Transaction.Builder> productTransaction(io.dmcapps.proto.catalog.Transaction.Builder transactionBuilder, TransactionEntity transactionEntity) {
        
        ProductEntity productEntity  = convertJSONToEntity(transactionBuilder.getPayload(), ProductEntity.class);
        io.dmcapps.proto.catalog.Product.Builder productBuilder = io.dmcapps.proto.catalog.Product.newBuilder();
        
        return Uni.createFrom().item(transactionBuilder)

            .onItem().transform(tx -> {transactionEmitter.send(KafkaRecord.of(transactionEntity.getId(), tx.build())); return tx;})

        
            .onItem().transformToUni(tx ->
                session.createQuery("SELECT g FROM ProductEntity g WHERE g.name = :name", ProductEntity.class)
                .setParameter("name", productEntity.getName())
                .getSingleResult()
                .onItem().transform(p -> tx.addError(PRODUCT_EXIST).setStatus(Status.FAILED))
                .onFailure().recoverWithItem(transactionBuilder))                
            
            .onItem().transformToUni(tx -> session.find(BrandEntity.class, productEntity.getBrand().getName())
                .onItem().transform(x -> {
                    if (x != null) {
                        productEntity.setBrand(x);
                    } else{
                        tx.addError(BRAND_NOT_EXIST).setStatus(Status.FAILED);
                    }
                    return tx;}))

            .onItem().transformToUni(tx -> session.find(CategoryEntity.class, productEntity.getCategory().getName())
                .onItem().transform(x -> {
                    if (x != null) {
                        productEntity.setCategory(x);
                    } else{
                        tx.addError(CATEGORY_NOT_EXIST).setStatus(Status.FAILED);
                    }
                    return tx;}))
                
            .onItem().transformToUni(tx -> {
                if (!tx.build().getStatus().equals(Status.FAILED)) {
                    return session.persist(productEntity)
                        .onItem().transformToUni(x -> session.flush()
                            .onItem().invoke(v -> LOGGER.info("Done saving product: " + productEntity.getId())))
                        .onItem().transform(v -> {
                            mergeEntityAndProtobuf(productEntity, productBuilder);
                            io.dmcapps.proto.catalog.Product productMessage = productBuilder
                                .build(); 
                            productEmitter.send(KafkaRecord.of(productEntity.getId(), productMessage));
                            return tx;
                        })
                        .onItem().transform(v -> tx.setStatus(Status.SUCCESS))
                        .onFailure().recoverWithItem(e -> tx.addError(e.getMessage()).setStatus(Status.FAILED));}
                return Uni.createFrom().item(tx);})
                .onItem().transform(tx -> {transactionEmitter.send(KafkaRecord.of(transactionEntity.getId(), tx.build())); return tx;});
    }
    

    public Uni<io.dmcapps.proto.catalog.Transaction.Builder> brandTransaction(io.dmcapps.proto.catalog.Transaction.Builder transactionBuilder, TransactionEntity transactionEntity) {
        
        BrandEntity brandEntity  = convertJSONToEntity(transactionBuilder.getPayload(), BrandEntity.class);
        io.dmcapps.proto.catalog.Brand.Builder brandBuilder = io.dmcapps.proto.catalog.Brand.newBuilder();
        
        return Uni.createFrom().item(transactionBuilder)
            .onItem().transform(tx -> {
                LOGGER.info(transactionEntity.getId());
                transactionEmitter.send(KafkaRecord.of(transactionEntity.getId(), transactionBuilder.build()));
                return tx;})

            .onItem().call(tx -> 
                session.find(BrandEntity.class, brandEntity.getName())
                .onItem().ifNotNull().transform(x -> 
                    tx.addError(BRAND_ALREADY_EXIST).setStatus(Status.FAILED)))

            .onItem().transformToUni(tx ->{
                if (!tx.build().getStatus().equals(Status.FAILED)) {
                    return session.persist(brandEntity)
                        .onItem().transformToUni(x -> session.flush()
                            .onItem().invoke(v -> LOGGER.info("Done saving brand: " + brandEntity.getName())))
                        .onItem().transform(v -> tx.setStatus(Status.SUCCESS))
                        .onItem().transform(v -> {
                            mergeEntityAndProtobuf(brandEntity, brandBuilder);
                            io.dmcapps.proto.catalog.Brand brandMessage = brandBuilder
                                .build(); 
                            brandEmitter.send(KafkaRecord.of(brandEntity.getName(), brandMessage));
                            return tx;
                        })
                        .onFailure().recoverWithItem(e -> tx.addError(e.getMessage()).setStatus(Status.FAILED));}
                return Uni.createFrom().item(tx);})
            .onItem().transform(tx -> {
                LOGGER.info(transactionEntity.getId());
                transactionEmitter.send(KafkaRecord.of(transactionEntity.getId(), tx.build()));
                return tx;});
            
    }

    public Uni<io.dmcapps.proto.catalog.Transaction.Builder> categoryTransaction(io.dmcapps.proto.catalog.Transaction.Builder transactionBuilder, TransactionEntity transactionEntity) {
        
        CategoryEntity categoryEntity  = convertJSONToEntity(transactionBuilder.getPayload(), CategoryEntity.class);
        io.dmcapps.proto.catalog.Category.Builder categoryBuilder = io.dmcapps.proto.catalog.Category.newBuilder();

        return Uni.createFrom().item(transactionBuilder)
        .onItem().transform(tx -> {transactionEmitter.send(KafkaRecord.of(transactionEntity.getId(), transactionBuilder.build())); return tx;})
        .onItem().call(tx -> 
            session.find(CategoryEntity.class, categoryEntity.getName())
            .onItem().ifNotNull().transform(x -> 
                tx.addError(CATEGORY_ALREADY_EXIST).setStatus(Status.FAILED)))

            .onItem().transformToUni(tx ->{
                if (!tx.build().getStatus().equals(Status.FAILED)) {
                    return session.persist(categoryEntity)
                        .onItem().transformToUni(x -> session.flush()
                            .onItem().invoke(v -> LOGGER.info("Done saving category: " + categoryEntity.getName())))
                        .onItem().transform(v -> tx.setStatus(Status.SUCCESS))
                        .onItem().transform(v -> {
                            mergeEntityAndProtobuf(categoryEntity, categoryBuilder);
                            io.dmcapps.proto.catalog.Category categoryMessage = categoryBuilder
                                .build(); 
                            categoryEmitter.send(KafkaRecord.of(categoryEntity.getName(), categoryMessage));
                            return tx;
                        })
                        .onFailure().recoverWithItem(e -> tx.addError(e.getMessage()).setStatus(Status.FAILED));}
                return Uni.createFrom().item(tx);})
        .onItem().transform(tx -> {transactionEmitter.send(KafkaRecord.of(transactionEntity.getId(), tx.build())); return tx;});
            

    }

    private void convertJSONToProtobuf(String json, com.google.protobuf.Message.Builder builder) {
        try {
            JsonFormat.parser().merge(json, builder);
        } catch (InvalidProtocolBufferException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
        
    private <T> void mergeEntityAndProtobuf(T entity, com.google.protobuf.Message.Builder builder) {
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
        try {
            return jsonb.fromJson(entityString, entityClass);
        } finally {
            try {
                jsonb.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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

    
}
