use color_eyre::eyre::{eyre, Result};
use serde::Deserialize;
use std::{collections::HashMap, path::Path};

use crate::hull::Hull;

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct Document {
    ofx: Ofx,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct Ofx {
    bankmsgsrsv1: Option<BankMsgsRsV1>,
    creditcardmsgsrsv1: Option<CreditCardMsgsRsV1>,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct BankMsgsRsV1 {
    stmttrnrs: Option<StmtTrnRs>,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct CreditCardMsgsRsV1 {
    ccstmttrnrs: Option<CcStmtTrnRs>,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct StmtTrnRs {
    stmtrs: Option<StmtRs>,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct CcStmtTrnRs {
    ccstmtrs: Option<CcStmtRs>,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct StmtRs {
    curdef: String,
    bankacctfrom: BankAcctFrom,
    banktranlist: Option<BankTranList>,
    ledgerbal: LedgerBal,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct CcStmtRs {
    curdef: String,
    ccacctfrom: CcAcctFrom,
    banktranlist: Option<BankTranList>,
    ledgerbal: LedgerBal,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct BankAcctFrom {
    acctid: String,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct CcAcctFrom {
    acctid: String,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct BankTranList {
    #[serde(rename = "stmttrn")]
    stmttrns: Vec<StmtTrn>,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct StmtTrn {
    trntype: String,
    dtposted: String,
    trnamt: String,
    fitid: String,
    name: Option<String>,
    payee: Option<Payee>,
    memo: Option<String>,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct Payee {
    name: String,
}

#[derive(Deserialize, Debug)]
#[serde(rename_all = "UPPERCASE")]
struct LedgerBal {
    balamt: String,
    dtasof: String,
}

impl From<StmtTrn> for HashMap<String, String> {
    fn from(value: StmtTrn) -> Self {
        [
            ("trntype", value.trntype),
            ("dtposted", value.dtposted),
            ("trnamt", value.trnamt),
            ("fitid", value.fitid),
        ]
        .into_iter()
        .chain(value.name.into_iter().map(|name| ("name", name)))
        .chain(value.payee.into_iter().map(|payee| ("payee", payee.name)))
        .chain(value.memo.into_iter().map(|memo| ("memo", memo)))
        .map(|(k, v)| (k.to_string(), v))
        .collect::<HashMap<_, _>>()
    }
}

pub(crate) fn parse(path: &Path, ofx2_content: &str) -> Result<Hull> {
    let doc = quick_xml::de::from_str::<'_, Document>(ofx2_content)?;

    match doc {
        Document {
            ofx:
                Ofx {
                    bankmsgsrsv1:
                        Some(BankMsgsRsV1 {
                            stmttrnrs:
                                Some(StmtTrnRs {
                                    stmtrs:
                                        Some(StmtRs {
                                            curdef,
                                            bankacctfrom: BankAcctFrom { acctid },
                                            banktranlist: Some(BankTranList { stmttrns }),
                                            ledgerbal: LedgerBal { balamt, dtasof },
                                        }),
                                }),
                        }),
                    creditcardmsgsrsv1: None,
                },
        } => Ok((curdef, acctid, balamt, dtasof, stmttrns)),

        Document {
            ofx:
                Ofx {
                    bankmsgsrsv1: None,
                    creditcardmsgsrsv1:
                        Some(CreditCardMsgsRsV1 {
                            ccstmttrnrs:
                                Some(CcStmtTrnRs {
                                    ccstmtrs:
                                        Some(CcStmtRs {
                                            curdef,
                                            ccacctfrom: CcAcctFrom { acctid },
                                            banktranlist: Some(BankTranList { stmttrns }),
                                            ledgerbal: LedgerBal { balamt, dtasof },
                                        }),
                                }),
                        }),
                },
        } => Ok((curdef, acctid, balamt, dtasof, stmttrns)),

        _ => Err(eyre!("unsupported OFX2 document {:?} {:?}", path, &doc)),
    }
    .map(|(curdef, acctid, balamt, dtasof, stmttrns)| Hull {
        hdr: [
            ("dialect", "ofx2".to_string()),
            ("curdef", curdef),
            ("acctid", acctid),
            ("balamt", balamt),
            ("dtasof", dtasof),
        ]
        .into_iter()
        .map(|(k, v)| (k.to_string(), v))
        .collect::<HashMap<_, _>>(),
        txns: stmttrns
            .into_iter()
            .map(Into::<HashMap<_, _>>::into)
            .collect::<Vec<_>>(),
    })
}
