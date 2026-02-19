use clap::Parser;
use color_eyre::eyre::{Context, Result, eyre};
use regex::Regex;
use std::path::PathBuf;
use std::{fs::read_to_string, path::Path, sync::LazyLock};

const ACCTID: &str = "acctid";
const BALAMT: &str = "balamt";
const CURDEF: &str = "curdef";
const DTASOF: &str = "dtasof";
const DTPOSTED: &str = "dtposted";
const FITID: &str = "fitid";
const MEMO: &str = "memo";
const NAME: &str = "name";
const OFXHEADER: &str = "ofxheader";
const PAYEE: &str = "payee";
const TRNAMT: &str = "trnamt";
const TRNTYPE: &str = "trntype";
const VERSION: &str = "version";

static BLANK_LINE_RE: LazyLock<Regex> = LazyLock::new(|| Regex::new("\r\n\\s*\r\n").unwrap());

static OFX1_HEADER_RE: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r#"\bOFXHEADER:(1[0-9][0-9])\b[^<]*\bVERSION:([0-9]+)"#).unwrap());

static OFX2_HEADER_RE: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r#"<\?xml[^>]*\?>\s*<\?OFX\s+OFXHEADER="(2[0-9][0-9])[^>]*\bVERSION="([0-9]+)""#)
        .unwrap()
});

#[derive(Parser)]
#[command(version, about = "Hull an OFX file for import into limabean-harvest", long_about = None)]
struct Cli {
    /// File to ingest
    ofx_path: PathBuf,
}

fn main() -> Result<()> {
    let out_w = &std::io::stdout();

    let cli = Cli::parse();

    let hulls = read_ofx_file(&cli.ofx_path)?;
    hulls.write(out_w)
}

pub(crate) fn read_ofx_file(path: &Path) -> Result<Hulls> {
    let content = read_to_string(path)
        .wrap_err_with(|| format!("Failed to read {}", path.to_string_lossy()))?;
    if let Some(captures) = OFX1_HEADER_RE.captures(&content) {
        if let Some(m) = BLANK_LINE_RE.find(&content) {
            let ofxheader = captures.get(1).unwrap().as_str();
            let version = captures.get(2).unwrap().as_str();
            ofx1::parse(path, &content[m.end()..], ofxheader, version)
        } else {
            Err(eyre!("failed to find end of OFX1 header in {:?}", path))
        }
    } else if let Some(captures) = OFX2_HEADER_RE.captures(&content) {
        let ofxheader = captures.get(1).unwrap().as_str();
        let version = captures.get(2).unwrap().as_str();
        ofx2::parse(path, &content, ofxheader, version)
    } else {
        Err(eyre!("unrecognised file content in {:?}", path))
    }
}

fn truncate_yyyymmdd(s: String) -> String {
    const MAXLEN: usize = 8;
    if s.len() > MAXLEN {
        s[..MAXLEN].to_string()
    } else {
        s
    }
}

#[path = "../hull.rs"]
mod hull;
use hull::Hulls;

#[path = "../ofx1.rs"]
mod ofx1;

#[path = "../ofx2.rs"]
mod ofx2;
