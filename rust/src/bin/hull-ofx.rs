use clap::Parser;
use color_eyre::eyre::{eyre, Context, Result};
use regex::Regex;
use std::path::PathBuf;
use std::{fs::read_to_string, path::Path, sync::LazyLock};

static BLANK_LINE_RE: LazyLock<Regex> = LazyLock::new(|| Regex::new("\r\n\\s*\r\n").unwrap());

static OFX2_HEADER_RE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r#"^<\?xml[^>]*\?>\s*<\?OFX\s+OFXHEADER="200""#).unwrap());


#[derive(Parser)]
#[command(version, about = "Hull an OFX file for import into limabean-harvest", long_about = None)]
struct Cli {
    /// File to ingest
    ofx_path: PathBuf,
}

fn main() -> Result<()> {
    let out_w = &std::io::stdout();

    let cli = Cli::parse();

    let hulls = Hulls(vec![read_ofx_file(&cli.ofx_path)?]);
    hulls.write(out_w)
}

pub(crate) fn read_ofx_file(path: &Path) -> Result<Hull> {
    let content = read_to_string(path)
        .wrap_err_with(|| format!("Failed to read {}", path.to_string_lossy()))?;
    if let Some(first_line) = content.lines().next() && first_line.trim() == "OFXHEADER:100" {
        if let Some(m) = BLANK_LINE_RE.find(&content) {
            ofx1::parse(path, &content[m.end()..])
        } else {
            Err(eyre!("failed to find end of OFX1 header in {:?}", path))
        }
    } else if OFX2_HEADER_RE.is_match( &content) {
        ofx2::parse(path, &content)
    } else {
        Err(eyre!("unrecognised file content in {:?}", path))
    }
}

#[path = "../hull.rs"]
mod hull;
use hull::{Hull, Hulls};

#[path = "../ofx1.rs"]
mod ofx1;

#[path = "../ofx2.rs"]
mod ofx2;
