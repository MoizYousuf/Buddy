// Plain-text CV -> clean one-column A4 PDF (same look as apply-kit's CVs). usage: node cvpdf.js <in.txt> <out.pdf>
const fs = require("fs");
const PDFDocument = require("pdfkit");
const [, , inp, outp] = process.argv;
const cv = fs.readFileSync(inp, "utf8");
const author = ((cv.split("\n")[0] || "").split("|")[0] || "").trim();
const doc = new PDFDocument({ size: "A4", margins: { top: 42, bottom: 42, left: 48, right: 48 }, info: { Title: "CV", Author: author } });
const out = fs.createWriteStream(outp);
doc.pipe(out);
const width = doc.page.width - 96;
const lines = cv.split("\n");
const name = (lines.shift() || "").trim();
doc.font("Helvetica-Bold").fontSize(20).fillColor("#111").text(name.split("|")[0].trim());
while (lines.length && lines[0].trim() && !(lines[0].trim().length < 45 && lines[0].trim() === lines[0].trim().toUpperCase() && /[A-Z]/.test(lines[0]))) {
  doc.font("Helvetica").fontSize(9.5).fillColor("#444").text(lines.shift().trim(), { width });
}
doc.moveDown(0.4);
for (const raw of lines) {
  const t = raw.trim();
  if (!t) continue;
  if (t.length < 45 && t === t.toUpperCase() && /[A-Z]/.test(t)) {
    doc.moveDown(0.5).font("Helvetica-Bold").fontSize(11).fillColor("#0f5c45").text(t.replace(/:$/, ""));
    const y = doc.y + 1;
    doc.moveTo(48, y).lineTo(48 + width, y).lineWidth(0.6).strokeColor("#999").stroke();
    doc.moveDown(0.3);
  } else if (/\|/.test(t) && /(19|20)\d\d|present/i.test(t) && !/^[•\-*–]/.test(t)) {
    doc.moveDown(0.2).font("Helvetica-Bold").fontSize(10.5).fillColor("#111").text(t, { width });
  } else if (/^[•\-*–]\s*/.test(t)) {
    doc.font("Helvetica").fontSize(10).fillColor("#111").text("•  " + t.replace(/^[•\-*–]\s*/, ""), { indent: 8, width: width - 8 });
  } else {
    doc.font("Helvetica").fontSize(10).fillColor("#111").text(t, { width });
  }
}
doc.end();
out.on("finish", () => console.log("ok"));
