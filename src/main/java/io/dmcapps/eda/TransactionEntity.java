package io.dmcapps.eda;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "transaction")
public class TransactionEntity {

    private String id;
    private String type;
    private String entityName;
    private String payload;
    private String status;
    private String[] error;
    private String link;

    public TransactionEntity() {
        //
    }
    
    @Id
    @Column
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    @Column
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    
    @Column
    public String getEntityName() {
        return entityName;
    }
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    @Column
    public String getPayload() {
        return payload;
    }
    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Column
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    @Column
    public String[] getError() {
        return error;
    }
    public void setError(String[] error) {
        this.error = error;
    }

}
