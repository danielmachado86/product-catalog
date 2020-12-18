package io.dmcapps.eda;

import javax.persistence.Entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
public class Brand extends PanacheEntity {

    public String name;
    public String picture;


}
