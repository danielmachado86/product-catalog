package io.dmcapps.eda;

import javax.persistence.Entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
public class Category extends PanacheEntity {

    public String parent;
    public String name;

}
