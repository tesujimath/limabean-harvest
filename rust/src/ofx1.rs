use color_eyre::eyre::{Result, WrapErr, eyre};
use serde::Deserialize;
use std::{collections::HashMap, path::Path};

use super::{
    ACCTID, BALAMT, CURDEF, DIALECT, DTASOF, DTPOSTED, FITID, MEMO, NAME, TRNAMT, TRNTYPE,
};
use crate::hull::{Hull, Hulls};

#[derive(Deserialize, Debug)]
struct Document {
    bankmsgsrsv1: Option<BankMsgsRsV1>,
    creditcardmsgsrsv1: Option<CreditCardMsgsRsV1>,
}

#[derive(Deserialize, Debug)]
struct BankMsgsRsV1 {
    stmttrnrs: StmtTrnRs,
}

#[derive(Deserialize, Debug)]
struct CreditCardMsgsRsV1 {
    ccstmttrnrs: CcStmtTrnRs,
}

#[derive(Deserialize, Debug)]
struct StmtTrnRs {
    stmtrs: StmtRs,
}

#[derive(Deserialize, Debug)]
struct CcStmtTrnRs {
    ccstmtrs: CcStmtRs,
}

#[derive(Deserialize, Debug)]
struct StmtRs {
    curdef: String,
    bankacctfrom: BankAcctFrom,
    banktranlist: BankTranList,
    ledgerbal: LedgerBal,
}

#[derive(Deserialize, Debug)]
struct CcStmtRs {
    curdef: String,
    ccacctfrom: CcAcctFrom,
    banktranlist: BankTranList,
    ledgerbal: LedgerBal,
}

#[derive(Deserialize, Debug)]
struct BankAcctFrom {
    acctid: String,
}

#[derive(Deserialize, Debug)]
struct CcAcctFrom {
    acctid: String,
}

#[derive(Deserialize, Debug)]
struct BankTranList {
    #[serde(rename = "stmttrn")]
    stmttrns: Vec<StmtTrn>,
}

#[derive(Deserialize, Debug)]
struct StmtTrn {
    trntype: String,
    dtposted: String,
    trnamt: String,
    fitid: String,
    name: String,
    memo: String,
}

#[derive(Deserialize, Debug)]
struct LedgerBal {
    balamt: String,
    dtasof: String,
}

impl From<StmtTrn> for HashMap<String, String> {
    fn from(value: StmtTrn) -> Self {
        [
            (TRNTYPE, value.trntype),
            (DTPOSTED, value.dtposted),
            (TRNAMT, value.trnamt),
            (FITID, value.fitid),
            (NAME, value.name),
            (MEMO, value.memo),
        ]
        .into_iter()
        .map(|(k, v)| (k.to_string(), v))
        .collect::<HashMap<_, _>>()
    }
}

pub(crate) fn parse(path: &Path, ofx_content: &str) -> Result<Hulls> {
    let sgml = sgmlish::Parser::builder()
        .lowercase_names()
        .expand_entities(|entity| match entity {
            "lt" => Some("<"),
            "gt" => Some(">"),
            "amp" => Some("&"),
            "nbsp" => Some(" "),
            _ => None,
        })
        .parse(ofx_content)
        .wrap_err_with(|| format!("Failed to parse OFX1 in {}", path.to_string_lossy()))?;
    let sgml = sgmlish::transforms::normalize_end_tags(sgml).wrap_err_with(|| {
        format!(
            "Failed to normalize OFX1 end tags in {}",
            path.to_string_lossy()
        )
    })?;
    let doc = sgmlish::from_fragment::<Document>(sgml)
        .wrap_err_with(|| format!("Failed to deserialize OFX1 in {}", path.to_string_lossy()))?;

    let hull = match doc {
        Document {
            bankmsgsrsv1:
                Some(BankMsgsRsV1 {
                    stmttrnrs:
                        StmtTrnRs {
                            stmtrs:
                                StmtRs {
                                    curdef,
                                    bankacctfrom: BankAcctFrom { acctid },
                                    banktranlist: BankTranList { stmttrns },
                                    ledgerbal: LedgerBal { balamt, dtasof },
                                },
                        },
                }),
            creditcardmsgsrsv1: None,
        } => Ok((curdef, acctid, balamt, dtasof, stmttrns)),

        Document {
            bankmsgsrsv1: None,
            creditcardmsgsrsv1:
                Some(CreditCardMsgsRsV1 {
                    ccstmttrnrs:
                        CcStmtTrnRs {
                            ccstmtrs:
                                CcStmtRs {
                                    curdef,
                                    ccacctfrom: CcAcctFrom { acctid },
                                    banktranlist: BankTranList { stmttrns },
                                    ledgerbal: LedgerBal { balamt, dtasof },
                                },
                        },
                }),
        } => Ok((curdef, acctid, balamt, dtasof, stmttrns)),

        _ => Err(eyre!("unsupported OFX1 document {:?}", path)),
    }
    .map(|(curdef, acctid, balamt, dtasof, stmttrns)| Hull {
        hdr: [
            (DIALECT, "ofx1".to_string()),
            (CURDEF, curdef),
            (ACCTID, acctid),
            (BALAMT, balamt),
            (DTASOF, dtasof),
        ]
        .into_iter()
        .map(|(k, v)| (k.to_string(), v))
        .collect::<HashMap<_, _>>(),
        txns: stmttrns
            .into_iter()
            .map(Into::<HashMap<_, _>>::into)
            .collect::<Vec<_>>(),
    })?;

    Ok(Hulls(vec![hull]))
}
