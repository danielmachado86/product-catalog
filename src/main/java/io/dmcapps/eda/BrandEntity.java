package io.dmcapps.eda;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "brand")
public class BrandEntity {

    private String name;
    private String picture;

    public BrandEntity() {
        //
    }

    public BrandEntity(String name) {
        setName(name);
	}

    @Id
    @Column
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @Column
    public String getPicture() {
        return picture;
    }
    public void setPicture(String picture) {
        this.picture = picture;
    }

}
