package com.splwg.cm.domain.billing.billableCharge.serviceQuantityRule;


import java.util.ArrayList;
import java.util.List;

import com.ibm.icu.math.BigDecimal;
import com.splwg.base.api.datatypes.Bool;
import com.splwg.base.api.datatypes.LookupHelper;
import com.splwg.base.api.sql.PreparedStatement;
import com.splwg.base.api.sql.SQLResultRow;
import com.splwg.base.domain.common.characteristicType.CharacteristicType;
import com.splwg.base.domain.common.characteristicType.CharacteristicType_Id;
import com.splwg.ccb.api.lookup.AcctTypeFlgLookup;
import com.splwg.ccb.api.lookup.BillStatusLookup;
import com.splwg.ccb.api.lookup.ServiceQuantityLookup;
import com.splwg.ccb.api.lookup.StatusFlagLookup;
import com.splwg.ccb.domain.admin.serviceQuantityIdentifier.ServiceQuantityIdentifier_Id;
import com.splwg.ccb.domain.admin.serviceQuantityRule.ServiceQuantityRuleComponent;
import com.splwg.ccb.domain.admin.timeOfUse.TimeOfUse_Id;
import com.splwg.ccb.domain.admin.unitOfMeasure.UnitOfMeasure_Id;
import com.splwg.ccb.domain.billing.billSegment.BillSegmentItemData;
import com.splwg.ccb.domain.billing.billSegment.BillSegmentReadData;
import com.splwg.ccb.domain.billing.billSegment.BillSegmentServiceQuantityData;
import com.splwg.ccb.domain.billing.billSegment.BillSegmentServiceQuantity_DTO;
import com.splwg.ccb.domain.billing.billSegment.BillSegmentServiceQuantity_Id;
import com.splwg.ccb.domain.billing.billSegment.BillSegment_Id;
import com.splwg.ccb.domain.common.characteristic.CharacteristicData;
import com.splwg.ccb.domain.customerinfo.account.Account;
import com.splwg.ccb.domain.customerinfo.account.AccountCharacteristic;
import com.splwg.ccb.domain.customerinfo.account.Account_Id;
import com.splwg.ccb.domain.customerinfo.person.Person;
import com.splwg.ccb.domain.customerinfo.person.PersonCharacteristic;
import com.splwg.ccb.domain.customerinfo.person.Person_Id;
import com.splwg.ccb.domain.customerinfo.serviceAgreement.ServiceAgreement;
import com.splwg.ccb.domain.pricing.priceitem.PriceItem_Id;
import com.splwg.ccb.domain.rate.ApplyRateData;
import com.splwg.shared.logging.Logger;
import com.splwg.shared.logging.LoggerFactory;

/**
 * @author Praveen Sathe
 *
@AlgorithmComponent (softParameters = { @AlgorithmSoftParameter (name = exclusionCharTypeCode, required = true, type = string)
 *            , @AlgorithmSoftParameter (name = sqiCode, required = true, type = string)
 *            , @AlgorithmSoftParameter (name = outputSQICode, required = true, type = string)
 *            , @AlgorithmSoftParameter (name = billableChargeFeedSourceFlag, required = true, type = string)})
 */
public class AggregateSpendAmtExclAlgo_Impl extends
AggregateSpendAmtExclAlgo_Gen implements
ServiceQuantityRuleComponent {


	private ApplyRateData applyRateData;
	private Account bsegAcct;
	private List<BillSegmentServiceQuantityData> billSegmentServiceQtyDataList;
	private ServiceAgreement serviceAgreement;
	private String excludePriceItems;
	private String usageAcctList;
	
	//SoftParameters
	private String exclusionCharTypeCd;
	private String sqiCode;
	private String outputSqiCode;
	private String feedSourceFlag;

	private static Logger logger = LoggerFactory.getLogger(AggregateSpendAmtExclAlgo_Impl.class);

	@Override
	public void invoke() {
		
		if (applyRateData == null || applyRateData.getServiceAgreement() == null) {
		    logger.debug("ApplyRateData or BillId is null or blank, stopping the execution");
		    return;
		}
		
		String invAcctId  = applyRateData.getBillId();
		
		if(isBlankOrNull(invAcctId)){
			logger.debug("Error While Fetching Bill Id ,Skipping the Execution"); 
			return;
		}
		
		Account_Id invAcct_Id = new Account_Id(invAcctId);
		Account invAcct = invAcct_Id.getEntity();
		
		if(invAcct == null){
			logger.info("Error While Invoice Account Entity ,Skipping the Execution"); 
			return;
		}
		AcctTypeFlgLookup acctFlag = LookupHelper.getLookupInstance(AcctTypeFlgLookup.class, AcctTypeFlgLookup.constants.INV_ACCT);
		if(!invAcct.getAcctUsageFlg().equalsIgnoreCase(acctFlag.trimmedValue())){
			logger.debug("Not Billing on Invoice Account , Skipping the Execution ");
        	return;
		}

		serviceAgreement = applyRateData.getServiceAgreement();
		bsegAcct = serviceAgreement.getAccount();

		//displayBillableChargeDetails();
		if (bsegAcct == null) {
		    logger.debug("Bseg's Account Id fetched is Null, stopping the execution");
		    return;
		}

		CharacteristicType_Id charTypeId = new CharacteristicType_Id(exclusionCharTypeCd.trim());
		CharacteristicType charType = charTypeId != null ? charTypeId.getEntity() : null;

		if (charType == null) {
		    logger.debug("Skipping further execution: Invalid Exclusions Char Type {" + getExclusionCharTypeCode().trim() + "}");
		    return;
		}

		AccountCharacteristic acctChar = bsegAcct.getEffectiveCharacteristic(charType);

		if (acctChar == null || isBlankOrNull(acctChar.getAdhocCharacteristicValue())) {
		    logger.debug("Account Exclusions Char Type {" + exclusionCharTypeCd.trim() + "} Not found on Account ID {" + bsegAcct.getId().getTrimmedValue() + "}");
		    Person mainPerson = fetchMainPerson(bsegAcct.getId().getTrimmedValue());

		    if (mainPerson == null) {
		        logger.debug("Error while Fetching Main Person Entity, Skipping the Execution");
		        return;
		    }

		    PersonCharacteristic perChar = mainPerson.getEffectiveCharacteristic(charType);

		    if (perChar == null || isBlankOrNull(perChar.getAdhocCharacteristicValue())) {
		        logger.debug("Error while Exclusion Char, Skipping the Execution");
		        return;
		    }

		    excludePriceItems = perChar.getAdhocCharacteristicValue();
		} else {
		    excludePriceItems = acctChar.getAdhocCharacteristicValue();
		}
		
		
	/*
		if(bsegAcct.getAcctUsageFlg().equalsIgnoreCase(acctFlag.trimmedValue())){
			invAcctId = bsegAcct.getId().getTrimmedValue();
		}else{
			invAcctId = fetchInvoiceAcct(bsegAcct.getId().getTrimmedValue());
		}
	*/
		if(isBlankOrNull(invAcctId)){
			logger.debug("Invoice Account Not Fetched ,Skiping Exceution");
			return;
		}
		
		usageAcctList = fetchUsageAccts(invAcctId);
		if(isBlankOrNull(usageAcctList)){
			logger.debug("No Usage Account Found. Skipping further Processing.");
			return;
		}

	    BigDecimal amount = calculateTotalSvcQty();
	    logger.debug("TOTSA Amount Calculated for Bseg : " + amount);
	    if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
	        addServiceQuantity(amount);
	    }
	}


	private void addServiceQuantity(BigDecimal amount) {
	    BillSegmentServiceQuantity_DTO serviceQtyDto = new BillSegmentServiceQuantity_DTO();
	    BillSegmentServiceQuantity_Id serviceQtyId = new BillSegmentServiceQuantity_Id(BillSegment_Id.NULL, null, null, outputSqiCode.trim());

	    serviceQtyDto.setInitialServiceQuantity(amount);
	    serviceQtyDto.setBillableServiceQuantity(amount);
	    serviceQtyDto.setId(serviceQtyId);

	    BillSegmentServiceQuantityData newServiceQtyData = BillSegmentServiceQuantityData.Factory.newInstance();
	    newServiceQtyData.setBillSegmentServiceQuantityDto(serviceQtyDto);

	    if (billSegmentServiceQtyDataList == null) {
	        billSegmentServiceQtyDataList = new ArrayList<>();
	    }
	    billSegmentServiceQtyDataList.add(newServiceQtyData);
	}

/*	private String fetchInvoiceAcct(String acctId) {
		String invAcctId = null;
		StatusFlagLookup statFlag = LookupHelper.getLookupInstance(StatusFlagLookup.class, StatusFlagLookup.constants.ACTIVE);
		PreparedStatement ps = null;
		SQLResultRow row = null;
		StringBuilder query = new StringBuilder();

		query.append("SELECT ")
		.append("ct.BILLING_ACCT_ID AS INVOICE_ACCT_ID ")
		.append("FROM ci_construct_linked_acct cla ")
		.append("JOIN ci_construct_template ct ON cla.CONSTRUCT_ID = ct.CONSTRUCT_ID ")
		.append("AND cla.VERSION_NUM = ct.VERSION_NUM ")
		.append("JOIN ci_construct c ON c.CONSTRUCT_ID = cla.CONSTRUCT_ID ")
		.append("AND c.VERSION_NUM = cla.VERSION_NUM ")
		.append("WHERE cla.ACCT_ID = :acctId ")
		.append("AND (cla.acct_eff_end_date IS NULL OR cla.acct_eff_end_date > :procDt) ")
		.append("AND cla.EXCLUDE_ACCT_SW) = :excldSw ")
		.append("AND c.STATUS_FLG = :statFlag ")
		.append("AND c.EFFT_START_DT) < :procDt ")
		.append("AND ct.BILLING_ACCT_ID IS NOT NULL ")
		.append("AND ct.BILLING_ACCT_ID <> ' ' ");

		logger.debug("Printing Query for output : " + query.toString());
		try{
			ps = createPreparedStatement(query.toString(), "Fetch Invoice Accounts");
			ps.bindString("acctId", acctId, "ACCT_ID");
			ps.bindString("statFlag", statFlag.trimmedValue(), "STATUS_FLG");
			ps.bindString("excldSw", "N", "EXCLUDE_ACCT_SW");
			ps.bindString("procDt", getProcessDateTime().toDate().toString(), "EFF_START_DT");

			row = ps.firstRow();
			if (row != null) {
				invAcctId = row.getString("INVOICE_ACCT_ID");
			}
		} catch (Exception e) { 
		    logger.error("Exception occurred while fetching Invoice Account: ", e);
		}finally {
			if (ps != null) {
				ps.close();
			}
		}

		return invAcctId;
	}
*/
	private String fetchUsageAccts(String acctId) {
	    StringBuilder accountIds = new StringBuilder();
	    PreparedStatement ps = null;
	    StatusFlagLookup statflag = LookupHelper.getLookupInstance(StatusFlagLookup.class, StatusFlagLookup.constants.ACTIVE);
	    
	    StringBuilder query = new StringBuilder();
	    query.append("SELECT lnkAcct.acct_id ")
	         .append("FROM ci_construct_linked_acct lnkAcct ")
	         .append("JOIN ci_construct_template temp ON lnkAcct.construct_id = temp.construct_id ")
	         .append("AND lnkAcct.version_num = temp.version_num ")
	         .append("JOIN ci_construct c ON c.construct_id = lnkAcct.construct_id ")
	         .append("AND c.version_num = lnkAcct.version_num ")
	         .append("WHERE lnkAcct.eff_start_date < :procDt ")
	         .append("AND lnkAcct.acct_eff_start_date < :procDt ")
	         .append("AND (lnkAcct.acct_eff_end_date IS NULL OR lnkAcct.acct_eff_end_date > :procDt) ")
	         .append("AND lnkAcct.exclude_acct_sw = :excldSw ")
	         .append("AND temp.billing_acct_id = :acctId ")
	         .append("AND c.status_flg = :statFlag");

	    try {
	        ps = createPreparedStatement(query.toString(), "Fetching Usage Account IDs for ACCTID");
	        ps.bindString("acctId", acctId, "BILLING_ACCT_ID");
	        ps.bindString("procDt", getProcessDateTime().toDate().toString(), "EFF_START_DATE");
	        ps.bindString("excldSw", "N", "EXCLUDE_ACCT_SW");
	        ps.bindString("statFlag", statflag.trimmedValue(), "STATUS_FLG");

	        List<SQLResultRow> result = ps.list();
	        if (result != null) {
	            for (SQLResultRow row : result) {
	                String accountId = row.getString("ACCT_ID");
	                if (!isBlankOrNull(accountId)) {
	                    if (accountIds.length() > 0) {
	                        accountIds.append(",");
	                    }
	                    accountIds.append("'").append(accountId).append("'");
	                }
	            }
	        }
	    } catch (Exception e) {
	        logger.error("Exception occurred while fetching account IDs for account: ", e);
	    } finally {
	        if (ps != null) {
	            ps.close();
	        }
	    }

	    return accountIds.toString();
	}

	private BigDecimal calculateTotalSvcQty() {
		BigDecimal totalSvcQty = null;
		PreparedStatement ps = null;
		SQLResultRow result = null;
		BigDecimal svcQty;
		BillStatusLookup billStatFlag = LookupHelper.getLookupInstance(BillStatusLookup.class, BillStatusLookup.constants.COMPLETE);

		String priceItmStr = getPriceItemStr(excludePriceItems);

		StringBuilder query = new StringBuilder();
		query.append("SELECT SUM(bsq.SVC_QTY) AS TOTAMT ")
		.append("FROM CI_BCHG_SQ bsq ")
		.append("INNER JOIN CI_BILL_CHG bc ON bsq.BILLABLE_CHG_ID = bc.BILLABLE_CHG_ID ")
		.append("LEFT JOIN CI_SA sa ON sa.SA_ID = bc.SA_ID ")
		.append("WHERE bsq.SQI_CD = rpad(:sqiCd,'8',' ') ")
		.append("  AND bc.PRICEITEM_CD IS NOT NULL AND bc.PRICEITEM_CD <> ' ' ");

		if(!isBlankOrNull(priceItmStr)){
			query.append("  AND bc.PRICEITEM_CD NOT IN ")
				 .append("("+ priceItmStr + ") ");
		}
		query.append("  AND bc.FEED_SOURCE_FLG = rpad(:sourceFlag,4,' ') ")
		.append("  AND NOT EXISTS (")
		.append("    SELECT 1 ")
		.append("    FROM CI_BSEG_CALC BSC ")
		.append("    JOIN CI_BSEG BS ON BS.BSEG_ID = BSC.BSEG_ID ")
		.append("    JOIN CI_BILL B ON B.BILL_ID = BS.BILL_ID ")
		.append("    WHERE B.BILL_STAT_FLG = :billSatFlg ")
		.append("    AND BSC.BILLABLE_CHG_ID = bc.BILLABLE_CHG_ID ")
		.append("  ) ")
		.append("  AND sa.sa_status_flg = :saStatFlag ")
		.append("  AND sa.sa_type_cd = rpad(:saTypeCd,8,' ') ")
		.append("  AND sa.ACCT_ID IN (");
		
		if(!isBlankOrNull(usageAcctList)){
			query.append(usageAcctList)
			.append(") ");
		}

		logger.debug("Final Query: " + query.toString());

		try {
			ps = createPreparedStatement(query.toString(), "Calculating Total SVC_QTY");
			ps.bindString("sourceFlag", feedSourceFlag.trim(), "FEED_SOURCE_FLAG");
			ps.bindString("sqiCd", getSQRuleParameter2().trim(), "SQI_CD");
			ps.bindString("saStatFlag","20" , "SA_STATUS_FLG");
			ps.bindString("saTypeCd","CARDS", "SA_TYPE_CD");
			ps.bindString("billSatFlg", billStatFlag.trimmedValue(), "BILL_STAT_FLG");
			

			result = ps.firstRow();
			if (result != null) {
				svcQty = result.getBigDecimal("TOTAMT");
				if (svcQty != null) {
					totalSvcQty = svcQty;
				}
			}
		} catch (Exception e) {
			logger.error("Exception occurred:", e);
		} finally {
			if (ps != null) {
				ps.close();
			}
		}

		return totalSvcQty;
	}

	private Person fetchMainPerson(String acctId) {
	    Person mainPerson = null;
	    
	    String acctMainPerId = fetchAcctMainPerId(acctId);
	    
	    if(isBlankOrNull(acctMainPerId))
	    	return null;
	    
	    Person_Id acctMainPer_Id = new Person_Id(acctMainPerId);
	    Person acctMainPer = (acctMainPer_Id != null) ? acctMainPer_Id.getEntity() : null;

	    if (acctMainPer == null) {
	        logger.debug("Error while Fetching Account's Main Person Entity, Skipping the Execution");
	        return null;
	    }

	    String parentPerId = fetchParentPersonId(acctMainPer.getId().getTrimmedValue());

	    if (!isBlankOrNull(parentPerId)) {
	        logger.debug("Hierarchy Person is Present");

	        Person_Id parentPer_Id = new Person_Id(parentPerId);
	        Person parentPerson = parentPer_Id != null ? parentPer_Id.getEntity() : null;

	        if (parentPerson == null) {
	            logger.debug("Failed to fetch Main Person entity for Person ID: " + parentPerId);
	            return null;
	        }

	        mainPerson = parentPerson;
	    } else {
	        logger.debug("Hierarchy Person is not present");
	        mainPerson = acctMainPer;
	    }

	    return mainPerson;
	}

	private String fetchAcctMainPerId(String acctId) {
	    String personId = null;
	    PreparedStatement ps = null;
	    SQLResultRow result = null;

	    StringBuilder str = new StringBuilder();
	    str.append("SELECT PER_ID ")
	       .append("FROM CI_ACCT_PER ")
	       .append("WHERE MAIN_CUST_SW = :mainCstSw ")
	       .append("AND ACCT_ID = :acctId");

	    String query = str.toString();

	    try {
	        ps = createPreparedStatement(query, "Fetching Main Person Id.");
	        ps.bindString("acctId", acctId, "ACCT_ID");
	        ps.bindString("mainCstSw", "Y", "MAIN_CUST_SW");

	        result = ps.firstRow();
	        if (result != null) {
	            personId = result.getString("PER_ID").trim();
	        }
	    } catch (Exception e) {
	        logger.debug("Exception Occurred: " + e.getMessage());
	    } finally {
	        if (ps != null) {
	            ps.close();
	        }
	    }

	    return personId;
	}

	private String fetchParentPersonId(String perId2) {
	    String parentPersonId = null;
	    PreparedStatement ps = null;
	    SQLResultRow result = null;

	    StringBuilder str = new StringBuilder();
	    str.append("SELECT PER_ID1 ")
	       .append("FROM CI_PER_PER ")
	       .append("WHERE PER_ID2 = :perId2");

	    String query = str.toString();

	    try {
	        ps = createPreparedStatement(query, "Fetching Parent Person Id.");
	        ps.bindString("perId2", perId2, "PER_ID2");

	        result = ps.firstRow();
	        if (result != null) {
	            parentPersonId = result.getString("PER_ID1").trim();
	        }
	    } catch (Exception e) {
	        logger.debug("Exception Occurred: " + e.getMessage());
	    } finally {
	        if (ps != null) {
	            ps.close();
	        }
	    }

	    return parentPersonId;
	}
	
	public String getPriceItemStr(String str){
		String formattedStr = null;
		StringBuilder priceItemsStr = new StringBuilder();
		if (!isBlankOrNull(str)) {
			String[] priceItems = str.split(",");
			for (int i = 0; i < priceItems.length; i++) {
				String priceitemCd = priceItems[i].trim();

	             if (!isBlankOrNull(priceitemCd)) {
	                 PriceItem_Id priceItemId = new PriceItem_Id(priceitemCd);
	                if (priceItemId.getEntity() == null) {
	                	addError(com.splwg.cm.domain.messageRepository.MessageRepository.invalidPriceItemCode(priceitemCd));
	                }
	            	priceItemsStr.append("'").append(priceItems[i].trim())
					.append("'");
					if (i < priceItems.length - 1) {
						priceItemsStr.append(", ");
					}
	             }
			}
		}
		if(priceItemsStr != null){
			formattedStr = priceItemsStr.toString();
		}
		return formattedStr;
	}
	@Override
	public ApplyRateData getApplyRateData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<BillSegmentItemData> getBillSegmentItemData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<BillSegmentReadData> getBillSegmentReadData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<BillSegmentServiceQuantityData> getBillSegmentServiceQuantityData() {
		// TODO Auto-generated method stub
		return this.billSegmentServiceQtyDataList;
	}

	@Override
	public List<CharacteristicData> getCharacteristicData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSQRuleParameter1() {
		return this.exclusionCharTypeCd;
	}

	@Override
	public String getSQRuleParameter10() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSQRuleParameter2() {
		// TODO Auto-generated method stub
		return this.sqiCode;
	}

	@Override
	public String getSQRuleParameter3() {
		// TODO Auto-generated method stub
		return this.outputSqiCode;
	}

	@Override
	public String getSQRuleParameter4() {
		// TODO Auto-generated method stub
		return this.feedSourceFlag;
	}

	@Override
	public String getSQRuleParameter5() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSQRuleParameter6() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSQRuleParameter7() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSQRuleParameter8() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSQRuleParameter9() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServiceQuantityIdentifier_Id getServiceQuantityIdentifierId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServiceQuantityLookup getServiceQuantityLookup() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Bool getShouldCreateBillingError() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TimeOfUse_Id getTimeOfUseId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public UnitOfMeasure_Id getUnitOfMeasureId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setApplyRateData(ApplyRateData applyRateData) {
		this.applyRateData = applyRateData;

	}

	@Override
	public void setBillSegmentItemData(List<BillSegmentItemData> arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setBillSegmentReadData(List<BillSegmentReadData> arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setBillSegmentServiceQuantityData(
			List<BillSegmentServiceQuantityData> arg0) {
		this.billSegmentServiceQtyDataList = arg0;

	}

	@Override
	public void setCharacteristicData(List<CharacteristicData> arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setSQRuleParameter1(String arg0) {
		this.exclusionCharTypeCd = arg0;

	}

	@Override
	public void setSQRuleParameter10(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setSQRuleParameter2(String arg0) {
		this.sqiCode = arg0;

	}

	@Override
	public void setSQRuleParameter3(String arg0) {
		this.outputSqiCode = arg0;

	}

	@Override
	public void setSQRuleParameter4(String arg0) {
		this.feedSourceFlag = arg0;

	}

	@Override
	public void setSQRuleParameter5(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setSQRuleParameter6(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setSQRuleParameter7(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setSQRuleParameter8(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setSQRuleParameter9(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setServiceQuantityIdentifierId(ServiceQuantityIdentifier_Id arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setServiceQuantityLookup(ServiceQuantityLookup arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setShouldCreateBillingError(Bool arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTimeOfUseId(TimeOfUse_Id arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setUnitOfMeasureId(UnitOfMeasure_Id arg0) {
		// TODO Auto-generated method stub

	}

}
