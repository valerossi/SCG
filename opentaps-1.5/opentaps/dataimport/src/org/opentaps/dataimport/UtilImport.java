package org.opentaps.dataimport;

import javolution.util.FastList;

import org.hibernate.Query;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.opentaps.base.entities.AcctgTrans;
import org.opentaps.base.entities.AcctgTransEntry;
import org.opentaps.base.entities.CustomTimePeriod;
import org.opentaps.base.entities.Enumeration;
import org.opentaps.base.entities.Geo;
import org.opentaps.base.entities.GeoType;
import org.opentaps.base.entities.GlAccount;
import org.opentaps.base.entities.GlAccountHistory;
import org.opentaps.base.entities.GlAccountOrganization;
import org.opentaps.base.entities.LoteTransaccion;
import org.opentaps.base.entities.NivelPresupuestal;
import org.opentaps.base.entities.Party;
import org.opentaps.base.entities.PartyGroup;
import org.opentaps.base.entities.PaymentMethod;
import org.opentaps.base.entities.ProductCategory;
import org.opentaps.base.entities.ProductCategoryType;
import org.opentaps.base.entities.TipoDocumento;
import org.opentaps.base.entities.WorkEffort;
import org.opentaps.dataimport.domain.Clasificacion;
import org.opentaps.dataimport.domain.ContenedorContable;
import org.opentaps.domain.ledger.LedgerRepositoryInterface;
import org.opentaps.foundation.entity.hibernate.Session;
import org.opentaps.foundation.repository.RepositoryException;

import com.ibm.icu.util.Calendar;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javolution.util.FastList;
import javolution.util.FastMap;

/**
 * Utility functions for importing.
 */
public class UtilImport {

	public static final String module = UtilImport.class.getName();

	/**
	 * For each role in the given list of roles, checks if it is already
	 * defined. Otherwise, creates a new PartyRole value for it (but does not
	 * store it yet). The resulting values can be stored at once in storeAll().
	 */
	public static List<GenericValue> ensurePartyRoles(String partyId,
			List<String> roleTypeIds, Delegator delegator)
			throws GenericEntityException {
		List<GenericValue> roles = FastList.newInstance();
		Map input = UtilMisc.toMap("partyId", partyId);
		for (String roleTypeId : roleTypeIds) {
			input.put("roleTypeId", roleTypeId);
			List<GenericValue> myRoles = delegator
					.findByAnd("PartyRole", input);
			if (myRoles.size() == 0) {
				roles.add(delegator.makeValue("PartyRole", input));
			}
		}
		return roles;
	}

	// makes a Map of format PostalAddress
	@SuppressWarnings("unchecked")
	public static GenericValue makePostalAddress(GenericValue contactMech,
			String companyName, String firstName, String lastName,
			String attnName, String address1, String address2, String city,
			String stateGeoCode, String postalCode, String postalCodeExt,
			String countryGeoCode, Delegator delegator) {
		Map<String, Object> postalAddress = FastMap.newInstance();

		// full name of the person built from first and last name
		String fullName = "";
		if (!UtilValidate.isEmpty(firstName)) {
			fullName = firstName + " " + lastName;
		} else if (UtilValidate.isEmpty(lastName)) {
			fullName = lastName;
		}

		if (!UtilValidate.isEmpty(companyName)) {
			postalAddress.put("toName", companyName);
		} else {
			postalAddress.put("toName", fullName);
		}

		postalAddress.put("attnName", attnName);
		postalAddress.put("contactMechId", contactMech.get("contactMechId"));
		postalAddress.put("address1", address1);
		postalAddress.put("address2", address2);
		postalAddress.put("city", city);
		postalAddress.put("stateProvinceGeoId", stateGeoCode);
		postalAddress.put("postalCode", postalCode);
		postalAddress.put("postalCodeExt", postalCodeExt);
		postalAddress.put("countryGeoId", countryGeoCode);

		return delegator.makeValue("PostalAddress", postalAddress);
	}

	// make a TelecomNumber
	@SuppressWarnings("unchecked")
	public static GenericValue makeTelecomNumber(GenericValue contactMech,
			String countryCode, String areaCode, String contactNumber,
			Delegator delegator) {
		Map<String, Object> telecomNumber = FastMap.newInstance();
		telecomNumber.put("contactMechId", contactMech.get("contactMechId"));
		telecomNumber.put("countryCode", countryCode);
		telecomNumber.put("areaCode", areaCode);
		telecomNumber.put("contactNumber", contactNumber);
		return delegator.makeValue("TelecomNumber", telecomNumber);
	}

	@SuppressWarnings("unchecked")
	public static GenericValue makeContactMechPurpose(
			String contactMechPurposeTypeId, GenericValue contactMech,
			String partyId, Timestamp now, Delegator delegator) {
		Map<String, Object> partyContactMechPurpose = FastMap.newInstance();
		partyContactMechPurpose.put("partyId", partyId);
		partyContactMechPurpose.put("fromDate", now);
		partyContactMechPurpose.put("contactMechId",
				contactMech.get("contactMechId"));
		partyContactMechPurpose.put("contactMechPurposeTypeId",
				contactMechPurposeTypeId);
		return delegator.makeValue("PartyContactMechPurpose",
				partyContactMechPurpose);
	}

	@SuppressWarnings("unchecked")
	public static List<GenericValue> makePartyWithRoles(String partyId,
			String partyTypeId, List<String> roleTypeIds, Delegator delegator) {
		List<GenericValue> partyValues = FastList.newInstance();
		partyValues
				.add(delegator.makeValue("Party", UtilMisc.toMap("partyId",
						partyId, "partyTypeId", partyTypeId)));
		for (Iterator<String> rti = roleTypeIds.iterator(); rti.hasNext();) {
			String nextRoleTypeId = (String) rti.next();
			partyValues.add(delegator.makeValue("PartyRole", UtilMisc.toMap(
					"partyId", partyId, "roleTypeId", nextRoleTypeId)));
		}
		return partyValues;
	}

	@SuppressWarnings("unchecked")
	public static GenericValue makePartySupplementalData(
			GenericValue partySupplementalData, String partyId,
			String fieldToUpdate, GenericValue contactMech, Delegator delegator) {

		if (partySupplementalData == null) {
			// create a new partySupplementalData
			Map<String, String> input = UtilMisc.toMap("partyId", partyId,
					fieldToUpdate, contactMech.getString("contactMechId"));
			return delegator.makeValue("PartySupplementalData", input);
		}

		// create or update the field
		partySupplementalData.set(fieldToUpdate,
				contactMech.get("contactMechId"));
		return null;
	}

	/**
	 * Decodes "0211" to "02/2011". If the input data is bad, then this returns
	 * null.
	 */
	public static String decodeExpireDate(String importDate) {
		if (importDate.length() != 4)
			return null;
		StringBuffer expireDate = new StringBuffer(importDate.substring(0, 2));
		expireDate.append("/20"); // hopefully code will not survive into the
									// 22nd century...
		expireDate.append(importDate.substring(2, 4));
		return expireDate.toString();
	}

	/**
	 * Autor: Jes�s Rodrigo Ruiz Merlin
	 * 
	 * @param ledger_repo
	 * @param nivelHijo
	 * @param idPadre
	 * @return true = padre valido, false = padre no valido
	 */
	public static boolean validaPadreEnum(
			LedgerRepositoryInterface ledger_repo, String nivelHijo,
			String idPadre, String tipo) throws RepositoryException {

		Debug.log("ValidandoPadreEnum: " + idPadre);
		List<Enumeration> enumeration = ledger_repo.findList(Enumeration.class,
				ledger_repo.map(Enumeration.Fields.sequenceId, idPadre,
						Enumeration.Fields.enumTypeId, tipo));

		if (enumeration.isEmpty()) {
			Debug.log("Lista Vacia");
			return false;
		}

		Debug.log("BUscando Nivel: hijo_" + nivelHijo + " padre_"
				+ enumeration.get(0).getNivelId());
		List<NivelPresupuestal> nivelP = ledger_repo.findList(
				NivelPresupuestal.class, ledger_repo.map(
						NivelPresupuestal.Fields.nivelId, nivelHijo,
						NivelPresupuestal.Fields.nivelPadreId,
						enumeration.get(0).getNivelId()));

		if (nivelP.isEmpty()) {
			Debug.log("Lista Vacia");
			return false;
		} else {
			Debug.log("Padre Valido");
			return true;
		}
	}

	/**
	 * Autor: Jes�s Rodrigo Ruiz Merlin
	 * 
	 * @param ledger_repo
	 * @param nivelHijo
	 * @param idPadre
	 * @return true = padre valido, false = padre no valido
	 */
	public static boolean validaPadreParty(
			LedgerRepositoryInterface ledger_repo, String nivelHijo,
			String idPadre) throws RepositoryException {

		Debug.log("ValidandoPadreParty");
		List<Party> party = ledger_repo.findList(Party.class,
				ledger_repo.map(Party.Fields.partyId, idPadre));

		if (party.isEmpty()) {
			Debug.log("Lista Vacia");
			return false;
		}

		Debug.log("Buscando Nivel");
		List<NivelPresupuestal> nivelP = ledger_repo.findList(
				NivelPresupuestal.class, ledger_repo.map(
						NivelPresupuestal.Fields.nivelId, nivelHijo,
						NivelPresupuestal.Fields.nivelPadreId, party.get(0)
								.getNivel_id()));

		if (nivelP.isEmpty()) {
			Debug.log("Lista Vacia");
			return false;
		} else {
			Debug.log("Padre Valido");
			return true;
		}
	}

	/**
	 * Autor: Jes�s Rodrigo Ruiz Merlin
	 * 
	 * @param ledger_repo
	 * @param nivelHijo
	 * @param idPadre
	 * @return true = padre valido, false = padre no valido
	 */
	public static boolean validaPadreGeo(LedgerRepositoryInterface ledger_repo,
			String tipoHijo, String idPadre) throws RepositoryException {

		Debug.log("ValidandoPadreGeo");
		List<Geo> geo = ledger_repo.findList(Geo.class,
				ledger_repo.map(Geo.Fields.geoId, idPadre));

		if (geo.isEmpty()) {
			Debug.log("Lista Vacia");
			return false;
		}

		Debug.log("Validando Tipo");
		List<GeoType> geoType = ledger_repo
				.findList(GeoType.class, ledger_repo.map(
						GeoType.Fields.geoTypeId, tipoHijo,
						GeoType.Fields.parentTypeId, geo.get(0).getGeoTypeId()));

		if (geoType.isEmpty()) {
			Debug.log("Lista Vacia");
			return false;
		} else {
			Debug.log("Padre Valido");
			return true;
		}
	}

	/**
	 * Autor: Jes�s Rodrigo Ruiz Merlin
	 * 
	 * @param ledger_repo
	 * @param nivelHijo
	 * @param idPadre
	 * @return true = padre valido, false = padre no valido
	 */
	public static boolean validaPadreProductCategory(
			LedgerRepositoryInterface ledger_repo, String tipoHijo,
			String idPadre) throws RepositoryException {

		Debug.log("ValidandoPadreProdCat");
		List<ProductCategory> prodCat = ledger_repo.findList(
				ProductCategory.class, ledger_repo.map(
						ProductCategory.Fields.productCategoryId, idPadre));

		if (prodCat.isEmpty()) {
			Debug.log("Lista Vacia");
			return false;
		}

		Debug.log("Validando Tipo");
		List<ProductCategoryType> prodCatType = ledger_repo.findList(
				ProductCategoryType.class, ledger_repo.map(
						ProductCategoryType.Fields.productCategoryTypeId,
						tipoHijo, ProductCategoryType.Fields.parentTypeId,
						prodCat.get(0).getProductCategoryTypeId()));

		if (prodCatType.isEmpty()) {
			Debug.log("Lista Vacia");
			return false;
		} else {
			Debug.log("Padre Valido");
			return true;
		}
	}

	public static ContenedorContable validaCiclo(String mensaje, String ciclo,
			Date fechaTrans) {
		ContenedorContable c = new ContenedorContable();
		Calendar cal = Calendar.getInstance();
		cal.setTime(fechaTrans);
		ciclo = "20" + ciclo;

		if (cal.get(Calendar.YEAR) != Integer.parseInt(ciclo)) {
			mensaje += "El ciclo no corresponde a la fecha contable de la transaccion, ";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static String obtenerCiclo(Date fechaTrans) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(fechaTrans);
		String ciclo = Integer.toString(cal.get(Calendar.YEAR));
		return ciclo;

	}

	public static ContenedorContable validaParty(String mensaje,
			LedgerRepositoryInterface ledger_repo, String id, String campo)
			throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		List<Party> parties = ledger_repo.findList(Party.class,
				ledger_repo.map(Party.Fields.externalId, id));
		if (parties.isEmpty()) {
			Debug.log("Error, " + campo + " no existe");
			mensaje += campo + " no existe, ";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static Party obtenParty(LedgerRepositoryInterface ledger_repo,
			String id) throws RepositoryException {
		List<Party> parties = ledger_repo.findList(Party.class,
				ledger_repo.map(Party.Fields.externalId, id));
		return parties.get(0);
	}

	public static ContenedorContable validaWorkEffort(String mensaje,
			LedgerRepositoryInterface ledger_repo, String id, String campo)
			throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		WorkEffort act = ledger_repo.findOne(WorkEffort.class,
				ledger_repo.map(WorkEffort.Fields.workEffortId, id));
		if (act == null) {
			Debug.log("Error, " + campo + " no existe");
			mensaje += campo + " no existe, ";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static WorkEffort obtenWorkEffort(
			LedgerRepositoryInterface ledger_repo, String id)
			throws RepositoryException {
		WorkEffort workEffort = ledger_repo.findOne(WorkEffort.class,
				ledger_repo.map(WorkEffort.Fields.workEffortId, id));
		return workEffort;
	}

	public static ContenedorContable validaProductCategory(String mensaje,
			LedgerRepositoryInterface ledger_repo, String id, String tipo,
			String campo) throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		List<ProductCategory> products = ledger_repo.findList(
				ProductCategory.class, ledger_repo.map(
						ProductCategory.Fields.categoryName, id,
						ProductCategory.Fields.productCategoryTypeId, tipo));
		if (products.isEmpty()) {
			Debug.log("Error, " + campo + " no existe");
			mensaje += campo + " no existe, ";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}

		return c;
	}

	public static ProductCategory obtenProductCategory(
			LedgerRepositoryInterface ledger_repo, String id, String tipo)
			throws RepositoryException {
		List<ProductCategory> products = ledger_repo.findList(
				ProductCategory.class, ledger_repo.map(
						ProductCategory.Fields.categoryName, id,
						ProductCategory.Fields.productCategoryTypeId, tipo));
		return products.get(0);
	}

	public static ContenedorContable validaGeo(String mensaje,
			LedgerRepositoryInterface ledger_repo, String id, String campo)
			throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		Geo loc = ledger_repo.findOne(Geo.class,
				ledger_repo.map(Geo.Fields.geoId, id));
		if (loc == null) {
			Debug.log("Error, " + campo + " no existe");
			mensaje += campo + " no existe, ";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static Geo obtenGeo(LedgerRepositoryInterface ledger_repo, String id)
			throws RepositoryException {
		Geo geo = ledger_repo.findOne(Geo.class,
				ledger_repo.map(Geo.Fields.geoId, id));
		return geo;
	}

	public static ContenedorContable validaEnumeration(String mensaje,
			LedgerRepositoryInterface ledger_repo, String id, String tipo,
			String campo) throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		List<Enumeration> enums = ledger_repo.findList(Enumeration.class,
				ledger_repo.map(Enumeration.Fields.sequenceId, id,
						Enumeration.Fields.enumTypeId, tipo));

		if (enums.isEmpty()) {
			Debug.log("Error, " + campo + " no existe");
			mensaje += campo + " no existe, ";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static Enumeration obtenEnumeration(
			LedgerRepositoryInterface ledger_repo, String id, String tipo)
			throws RepositoryException {
		List<Enumeration> enums = ledger_repo.findList(Enumeration.class,
				ledger_repo.map(Enumeration.Fields.sequenceId, id,
						Enumeration.Fields.enumTypeId, tipo));
		return enums.get(0);
	}

	public static ContenedorContable validaClasificaciones(List<Clasificacion> lista,
			LedgerRepositoryInterface ledger_repo, String tipo, Date fechaTrans) {
		ContenedorContable contenedor = new ContenedorContable();
		String mensaje = null;
		for (Clasificacion c : lista) {
			String tipoClasif = c.getTipoObjeto();
			String valorClasif = c.getValor();
			String tipoEnum = c.getTipoEnum();
			try {
				if (tipoClasif.equals("Party")) {
					mensaje += validaParty(mensaje, ledger_repo, valorClasif,
							"ADMINISTRATIVA").getMensaje();
					if(mensaje.equals(""))
						{
							Party p = obtenParty(ledger_repo, valorClasif);
							mensaje += validaVigenciaParty(mensaje, "ADMINISTRATIVA", p).getMensaje();
						}
					
				} else if (tipoClasif.equals("Geo")) {
					mensaje += validaGeo(mensaje, ledger_repo, valorClasif,
							"GEOGRAFICA").getMensaje();
				} else if (tipoClasif.equals("WorkEffort")) {
					mensaje += validaWorkEffort(mensaje, ledger_repo,
							valorClasif, "PROYECTO").getMensaje();
					if(mensaje.equals("")){
						WorkEffort w = obtenWorkEffort(ledger_repo, valorClasif);
						mensaje += validaVigenciaWorkEffort(mensaje, "PROYECTO", w,
								fechaTrans).getMensaje();
					}
					
				} else if (tipoClasif.equals("ProductCategory")) {
					if (tipo.equals("I")) {
						mensaje += validaProductCategory(mensaje, ledger_repo,
								valorClasif, buscaHojaCri(ledger_repo), "RUBRO DEL INGRESO").getMensaje();
						if(mensaje.equals(""))
						{
							ProductCategory p = obtenProductCategory(ledger_repo,
								valorClasif, buscaHojaCri(ledger_repo));
							mensaje += validaVigenciaProductCategory(mensaje,
									"RUBRO DEL INGRESO", p, fechaTrans).getMensaje();
						}
						
					} else {
						mensaje += validaProductCategory(mensaje, ledger_repo,
								valorClasif, buscaHojaCog(ledger_repo),
								"PRODUCTO ESPECIFICO").getMensaje();
						if(mensaje.equals("")){
						ProductCategory p = obtenProductCategory(ledger_repo,
								valorClasif, buscaHojaCog(ledger_repo));
						mensaje += validaVigenciaProductCategory(mensaje,
								"PRODUCTO ESPECIFICO", p, fechaTrans).getMensaje();
						}
					}
				} else if (tipoClasif.equals("EnumerationType")) {
					mensaje += validaEnumeration(mensaje, ledger_repo,
							valorClasif, tipoEnum, valorClasif).getMensaje();
					if(mensaje.equals("")){
					Enumeration e = obtenEnumeration(ledger_repo, valorClasif,
							tipoEnum);
					mensaje += validaVigencia(mensaje, valorClasif, e,
							fechaTrans).getMensaje();}
				}
			} catch (Exception e) {
				//
			}
		}
		return contenedor;
	}

	public static ContenedorContable validaVigencia(String mensaje, String campo,
			Enumeration enumeration, Date fechaTrans)
			throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		if (!enumeration.getFechaInicio().before(fechaTrans)
				|| !enumeration.getFechaFin().after(fechaTrans)) {
			Debug.log("Error, " + campo + " no vigente");
			mensaje += campo + " no vigente";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static ContenedorContable validaVigenciaProductCategory(String mensaje,
			String campo, ProductCategory p, Date fechaTrans)
			throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		if (!p.getFechaInicio().before(fechaTrans)
				|| !p.getFechaFin().after(fechaTrans)) {
			Debug.log("Error, " + campo + " no vigente");
			mensaje += campo + " no vigente";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static ContenedorContable validaVigenciaWorkEffort(String mensaje, String campo,
			WorkEffort w, Date fechaTrans) throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		if (!w.getEstimatedStartDate().before(fechaTrans)
				|| !w.getEstimatedCompletionDate().after(fechaTrans)) {
			Debug.log("Error, " + campo + " no vigente");
			mensaje += campo + " no vigente";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static ContenedorContable validaVigenciaParty(String mensaje, String campo,
			Party party) throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		if (party.getState().equals("I")) {
			Debug.log("Error, " + campo + " no vigente");
			mensaje += campo + " no vigente";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static ContenedorContable validaTipoDoc(String mensaje,
			LedgerRepositoryInterface ledger_repo, String tipo)
			throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		TipoDocumento type = ledger_repo.findOne(TipoDocumento.class,
				ledger_repo.map(TipoDocumento.Fields.idTipoDoc, tipo));
		if (type == null) {
			Debug.log("Error, tipoDoc no existe");
			mensaje += "tipo documento no existe, ";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static TipoDocumento obtenTipoDocumento(
			LedgerRepositoryInterface ledger_repo, String tipo)
			throws RepositoryException {
		TipoDocumento tipoDocumento = ledger_repo.findOne(TipoDocumento.class,
				ledger_repo.map(TipoDocumento.Fields.idTipoDoc, tipo));

		return tipoDocumento;
	}

	public static ContenedorContable validaPago(String mensaje,
			LedgerRepositoryInterface ledger_repo, String tipo)
			throws RepositoryException {
		ContenedorContable c = new ContenedorContable();
		PaymentMethod payment = ledger_repo.findOne(PaymentMethod.class,
				ledger_repo.map(PaymentMethod.Fields.paymentMethodId, tipo));
		if (payment == null) {
			Debug.log("Error, idPago no existe");
			mensaje += "idPago no existe, ";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static AcctgTransEntry generaAcctgTransEntry(AcctgTrans transaccion,
			String organizacionPartyId, String seqId, String flag,
			String cuenta, String sfeId) {
		// C/D
		Debug.log("Empieza AcctgTransEntry " + cuenta);

		AcctgTransEntry acctgentry = new AcctgTransEntry();
		acctgentry.setAcctgTransId(transaccion.getAcctgTransId());
		acctgentry.setAcctgTransEntrySeqId(seqId);
		acctgentry.setAcctgTransEntryTypeId("_NA_");
		acctgentry.setDescription(transaccion.getDescription());
		acctgentry.setGlAccountId(cuenta);
		Debug.log("Organization.- " + organizacionPartyId);
		acctgentry.setOrganizationPartyId(organizacionPartyId);
		acctgentry.setPartyId(organizacionPartyId);
		acctgentry.setAmount(transaccion.getPostedAmount());
		acctgentry.setCurrencyUomId("MXN");
		acctgentry.setDebitCreditFlag(flag);
		acctgentry.setReconcileStatusId("AES_NOT_RECONCILED");
		// Tags seteados.
		if (sfeId != null) {
			acctgentry.setAcctgTagEnumId3(sfeId);
		}
		return acctgentry;
	}

	public static GlAccountOrganization actualizaGlAccountOrganization(
			LedgerRepositoryInterface ledger_repo, BigDecimal monto,
			String cuenta, String organizacionPartyId, String naturaleza)
			throws RepositoryException {

		Debug.log("Empieza GlAccountOrganization " + cuenta);

		// Se busca la naturaleza de la cuenta.
		GlAccount glAccount = ledger_repo.findOne(GlAccount.class,
				ledger_repo.map(GlAccount.Fields.glAccountId, cuenta));

		// GlAccountOrganization
		GlAccountOrganization glAccountOrganization = ledger_repo.findOne(
				GlAccountOrganization.class, ledger_repo.map(
						GlAccountOrganization.Fields.glAccountId, cuenta,
						GlAccountOrganization.Fields.organizationPartyId,
						organizacionPartyId));

		if (glAccount.getNaturaleza().equalsIgnoreCase(naturaleza)) {
			if (glAccountOrganization.getPostedBalance() == null) {
				glAccountOrganization.setPostedBalance(monto);
			} else {
				glAccountOrganization.setPostedBalance(glAccountOrganization
						.getPostedBalance().add(monto));
			}
		} else {
			if (glAccountOrganization.getPostedBalance() == null) {
				glAccountOrganization.setPostedBalance(BigDecimal.ZERO);
			}
			glAccountOrganization.setPostedBalance(glAccountOrganization
					.getPostedBalance().subtract(monto));

		}

		return glAccountOrganization;
	}

	public static String getAcctgTransIdDiario(String ref, String sec,
			String tipo) {
		return ref + "-" + sec + "-" + tipo;
	}

	public static String obtenPadreParty(LedgerRepositoryInterface ledger_repo,
			String idHijo) throws RepositoryException {
		Debug.log("Busqueda de PadreParty");
		PartyGroup partyGroup = ledger_repo.findOne(PartyGroup.class,
				ledger_repo.map(PartyGroup.Fields.partyId, idHijo));
		return partyGroup.getParent_id();
	}

	public static String obtenPadreProductCategory(
			LedgerRepositoryInterface ledger_repo, String idHijo)
			throws RepositoryException {
		Debug.log("Busqueda de PadreProductCategory");
		ProductCategory product = ledger_repo.findOne(ProductCategory.class,
				ledger_repo.map(ProductCategory.Fields.productCategoryId,
						idHijo));
		return product.getPrimaryParentCategoryId();
	}

	public static String obtenPadreEnumeration(
			LedgerRepositoryInterface ledger_repo, String idHijo)
			throws RepositoryException {
		Debug.log("Busqueda de PadreEnumeration");
		Enumeration enumeration = ledger_repo.findOne(Enumeration.class,
				ledger_repo.map(Enumeration.Fields.enumId, idHijo));
		return enumeration.getParentEnumId();
	}

	public static String obtenPadreGeo(LedgerRepositoryInterface ledger_repo,
			String idHijo) throws RepositoryException {
		Debug.log("Busqueda de PadreGeo");
		Geo geo = ledger_repo.findOne(Geo.class,
				ledger_repo.map(Geo.Fields.geoId, idHijo));
		return geo.getGeoCode();
	}

	public static String obtenPadreWorkEffort(
			LedgerRepositoryInterface ledger_repo, String idHijo)
			throws RepositoryException {
		Debug.log("Busqueda de PadreWorkEffort");
		WorkEffort work = ledger_repo.findOne(WorkEffort.class,
				ledger_repo.map(WorkEffort.Fields.workEffortId, idHijo));
		return work.getWorkEffortParentId();
	}

	public static ContenedorContable validaMonto(BigDecimal monto, String mensaje) {
		ContenedorContable c = new ContenedorContable();
		if (monto.doubleValue() <= 0) {
			Debug.log("Error, el monto debe ser mayor a 0");
			mensaje += "el monto debe ser mayor a 0, ";
			Debug.log(mensaje);
			c.setMensaje(mensaje);
		}
		return c;
	}

	public static boolean validaLote(LedgerRepositoryInterface ledger_repo,
			String lote, String tipo) throws RepositoryException {
		LoteTransaccion loteTrans = ledger_repo.findOne(LoteTransaccion.class,
				ledger_repo.map(LoteTransaccion.Fields.idLote, lote,
						LoteTransaccion.Fields.tipoTransaccion, tipo));
		if (loteTrans == null) {
			return true;
		} else {
			Debug.log("El lote ya existe.");
			return false;
		}
	}

	public static List<CustomTimePeriod> obtenPeriodos(
			LedgerRepositoryInterface ledger_repo, String organizacionPartyId,
			Date fechaTrans) throws RepositoryException {
		Debug.log("Fecha.- " + fechaTrans);
		List<CustomTimePeriod> periodos = ledger_repo.findList(
				CustomTimePeriod.class, ledger_repo.map(
						CustomTimePeriod.Fields.organizationPartyId,
						organizacionPartyId, CustomTimePeriod.Fields.isClosed,
						"N"));
		List<CustomTimePeriod> periodosAplicables = new ArrayList<CustomTimePeriod>();
		for (CustomTimePeriod periodo : periodos) {
			if (fechaTrans.after(periodo.getFromDate())
					&& fechaTrans.before(periodo.getThruDate())) {
				periodosAplicables.add(periodo);
			}
		}
		Debug.log("Periodos regresados.- " + periodosAplicables.size());
		return periodosAplicables;
	}

	public static List<GlAccountHistory> actualizaGlAccountHistories(
			LedgerRepositoryInterface ledger_repo,
			List<CustomTimePeriod> periodos, String cuenta, BigDecimal monto,
			String tipo) throws RepositoryException {
		List<GlAccountHistory> glAccountHistories = new ArrayList<GlAccountHistory>();

		for (CustomTimePeriod periodo : periodos) {
			GlAccountHistory glAccountHistory = ledger_repo.findOne(
					GlAccountHistory.class, ledger_repo.map(
							GlAccountHistory.Fields.glAccountId, cuenta,
							GlAccountHistory.Fields.organizationPartyId,
							periodo.getOrganizationPartyId(),
							GlAccountHistory.Fields.customTimePeriodId,
							periodo.getCustomTimePeriodId()));

			if (glAccountHistory == null) {
				Debug.log("No existe History");
				glAccountHistory = new GlAccountHistory();
				glAccountHistory.setGlAccountId(cuenta);
				glAccountHistory.setOrganizationPartyId(periodo
						.getOrganizationPartyId());
				glAccountHistory.setCustomTimePeriodId(periodo
						.getCustomTimePeriodId());
			}

			if (tipo.equalsIgnoreCase("Credit")) {
				if (glAccountHistory.getPostedCredits() == null) {
					glAccountHistory.setPostedCredits(monto);
				} else {
					glAccountHistory.setPostedCredits(glAccountHistory
							.getPostedCredits().add(monto));
				}

			} else {
				if (glAccountHistory.getPostedDebits() == null) {
					glAccountHistory.setPostedDebits(monto);
				} else {
					glAccountHistory.setPostedDebits(glAccountHistory
							.getPostedDebits().add(monto));
				}
			}

			glAccountHistories.add(glAccountHistory);
		}
		return glAccountHistories;
	}

	/**
	 * Autor: Esmeralda Cercas Ortiz
	 * 
	 * @param ledger_repo
	 * @param nivel
	 * @return true = nivel valido, false = nivel no valido
	 */
	public static boolean validaNivel(LedgerRepositoryInterface ledger_repo,
			String nivel) throws RepositoryException {

		Debug.log("Buscando Nivel");
		List<NivelPresupuestal> nivelP = ledger_repo.findList(
				NivelPresupuestal.class,
				ledger_repo.map(NivelPresupuestal.Fields.nivelId, nivel));

		if (nivelP.isEmpty()) {
			Debug.log("Nivel no valido");
			return false;
		} else {
			Debug.log("Nivel valido");
			return true;
		}
	}

	/**
	 * Autor: Esmeralda Cercas Ortiz entitie GeoType
	 * 
	 * @param ledger_repo
	 * @param Type
	 * @return true = Tipo valido, false = Tipo no valido
	 */
	public static boolean validaTipoGeo(LedgerRepositoryInterface ledger_repo,
			String Type) throws RepositoryException {

		Debug.log("Buscando Tipo");
		List<GeoType> type = ledger_repo.findList(GeoType.class,
				ledger_repo.map(GeoType.Fields.geoTypeId, Type));

		if (type.isEmpty()) {
			Debug.log("Tipo no valido");
			return false;
		} else {
			Debug.log("Tipo valido");
			return true;
		}
	}

	/**
	 * Autor: Esmeralda Cercas Ortiz entitie ProductCategoryType
	 * 
	 * @param ledger_repo
	 * @param nivel
	 * @return true = Tipo valido, false = tipo no valido
	 */
	public static boolean validaTipoProductCategory(
			LedgerRepositoryInterface ledger_repo, String type)
			throws RepositoryException {

		Debug.log("Buscando Tipo ProductCategory");
		List<ProductCategoryType> nivelP = ledger_repo
				.findList(ProductCategoryType.class, ledger_repo.map(
						ProductCategoryType.Fields.productCategoryTypeId, type));

		if (nivelP.isEmpty()) {
			Debug.log("Tipo no valido");
			return false;
		} else {
			Debug.log("Tipo valido");
			return true;
		}
	}

	public static String buscaHojaNivelPresupuestal(String tipo, Session session)
			throws RepositoryException {
		return session
				.createQuery(
						"select NIVEL_ID from NIVEL_PRESUPUESTAL"
								+ "where NIVEL_ID not in (select NIVEL_PADRE_ID from NIVEL_PRESUPUESTAL"
								+ "where CLASIFICACION_ID = "
								+ tipo
								+ " and NIVEL_PADRE_ID is not null) and CLASIFICACION_ID = "
								+ tipo).list().get(0).toString();
	}

	public static String buscaHojaGeo(LedgerRepositoryInterface ledger_repo)
			throws RepositoryException {
		boolean rama = true;
		String tipo = "COUNTRY";

		do {
			List<GeoType> geoTypes = ledger_repo.findList(GeoType.class,
					ledger_repo.map(GeoType.Fields.parentTypeId, tipo));
			if(!geoTypes.isEmpty()){
				tipo = geoTypes.get(0).getGeoTypeId(); 
			}else{
				rama = false;
			}
		} while (rama);
		return tipo;
	}
	
	public static String buscaHojaCri(LedgerRepositoryInterface ledger_repo)
			throws RepositoryException {
		boolean rama = true;
		String tipo = "CRI";

		do {
			List<ProductCategoryType> prodCatTypes = ledger_repo.findList(ProductCategoryType.class,
					ledger_repo.map(ProductCategoryType.Fields.parentTypeId, tipo));
			if(!prodCatTypes.isEmpty()){
				tipo = prodCatTypes.get(0).getProductCategoryTypeId(); 
			}else{
				rama = false;
			}
		} while (rama);
		return tipo;
	}
	
	public static String buscaHojaCog(LedgerRepositoryInterface ledger_repo)
			throws RepositoryException {
		boolean rama = true;
		String tipo = "COG";

		do {
			List<ProductCategoryType> prodCatTypes = ledger_repo.findList(ProductCategoryType.class,
					ledger_repo.map(ProductCategoryType.Fields.parentTypeId, tipo));
			if(!prodCatTypes.isEmpty()){
				tipo = prodCatTypes.get(0).getProductCategoryTypeId(); 
			}else{
				rama = false;
			}
		} while (rama);
		return tipo;
	}

}
