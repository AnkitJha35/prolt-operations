package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "user_details", schema = "public")
public class UserDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "address_pin_code")
    private Integer addressPinCode;

    @Column(name = "address_state")
    private String addressState;

    @Column(name = "billing_address_line1")
    private String billingAddressLine1;

    @Column(name = "billing_address_line2")
    private String billingAddressLine2;

    @Column(name = "billing_pin_code")
    private Integer billingPinCode;

    @Column(name = "billing_state")
    private String billingState;

    @Column(name = "contact_person_name")
    private String contactPersonName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_whatsapp_number")
    private String contactWhatsappNumber;

    @Column(name = "contact_alternate_phone")
    private String contactAlternatePhone;

    @Column(name = "factory_name")
    private String factoryName;

    @Column(name = "gst_number")
    private String gstNumber;

    @Column(name = "sanctioned_load_kw")
    private BigDecimal sanctionedLoadKw;

    @Column(name = "discom")
    private String discom;

    @Column(name = "state")
    private String state;

    @Column(name = "supply_voltage")
    private String supplyVoltage;

    @Column(name = "category")
    private String category;

    @Column(name = "sub_category")
    private String subCategory;

    protected UserDetails() {
    }

    public static UserDetails create() {
        return new UserDetails();
    }

    public UserDetails(String customerId, String addressLine1, String addressLine2, Integer addressPinCode, String addressState) {
        this.customerId = customerId;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressPinCode = addressPinCode;
        this.addressState = addressState;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public Integer getAddressPinCode() {
        return addressPinCode;
    }

    public void setAddressPinCode(Integer addressPinCode) {
        this.addressPinCode = addressPinCode;
    }

    public String getAddressState() {
        return addressState;
    }

    public void setAddressState(String addressState) {
        this.addressState = addressState;
    }

    public String getBillingAddressLine1() {
        return billingAddressLine1;
    }

    public void setBillingAddressLine1(String billingAddressLine1) {
        this.billingAddressLine1 = billingAddressLine1;
    }

    public String getBillingAddressLine2() {
        return billingAddressLine2;
    }

    public void setBillingAddressLine2(String billingAddressLine2) {
        this.billingAddressLine2 = billingAddressLine2;
    }

    public Integer getBillingPinCode() {
        return billingPinCode;
    }

    public void setBillingPinCode(Integer billingPinCode) {
        this.billingPinCode = billingPinCode;
    }

    public String getBillingState() {
        return billingState;
    }

    public void setBillingState(String billingState) {
        this.billingState = billingState;
    }

    public String getContactPersonName() {
        return contactPersonName;
    }

    public void setContactPersonName(String contactPersonName) {
        this.contactPersonName = contactPersonName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactWhatsappNumber() {
        return contactWhatsappNumber;
    }

    public void setContactWhatsappNumber(String contactWhatsappNumber) {
        this.contactWhatsappNumber = contactWhatsappNumber;
    }

    public String getContactAlternatePhone() {
        return contactAlternatePhone;
    }

    public void setContactAlternatePhone(String contactAlternatePhone) {
        this.contactAlternatePhone = contactAlternatePhone;
    }

    public String getFactoryName() {
        return factoryName;
    }

    public void setFactoryName(String factoryName) {
        this.factoryName = factoryName;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }

    public BigDecimal getSanctionedLoadKw() {
        return sanctionedLoadKw;
    }

    public void setSanctionedLoadKw(BigDecimal sanctionedLoadKw) {
        this.sanctionedLoadKw = sanctionedLoadKw;
    }

    public String getDiscom() {
        return discom;
    }

    public void setDiscom(String discom) {
        this.discom = discom;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getSupplyVoltage() {
        return supplyVoltage;
    }

    public void setSupplyVoltage(String supplyVoltage) {
        this.supplyVoltage = supplyVoltage;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserDetails that = (UserDetails) o;
        return Objects.equals(customerId, that.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId);
    }

    @Override
    public String toString() {
        return "UserDetails{" + "customerId=" + customerId + ", " + "addressLine1=" + addressLine1 + ", " + "addressLine2=" + addressLine2 + ", " + "addressPinCode=" + addressPinCode + ", " + "addressState=" + addressState + ", " + "billingAddressLine1=" + billingAddressLine1 + ", " + "billingAddressLine2=" + billingAddressLine2 + ", " + "billingPinCode=" + billingPinCode + ", " + "billingState=" + billingState + ", " + "contactPersonName=" + contactPersonName + ", " + "contactEmail=" + contactEmail + ", " + "contactPhone=" + contactPhone + ", " + "contactWhatsappNumber=" + contactWhatsappNumber + ", " + "contactAlternatePhone=" + contactAlternatePhone + ", " + "factoryName=" + factoryName + ", " + "gstNumber=" + gstNumber + ", " + "sanctionedLoadKw=" + sanctionedLoadKw + ", " + "discom=" + discom + ", " + "state=" + state + ", " + "supplyVoltage=" + supplyVoltage + ", " + "category=" + category + ", " + "subCategory=" + subCategory + "}";
    }
}
