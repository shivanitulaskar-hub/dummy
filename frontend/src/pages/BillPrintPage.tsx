import { useEffect } from 'react';
import { Alert, Box, Button, CircularProgress, Stack, ToggleButton, ToggleButtonGroup } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import PrintIcon from '@mui/icons-material/Print';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { fetchInvoice } from '../api/bills';
import { toUserMessage } from '../api/errors';
import type { Invoice } from '../api/types';
import { formatInvoiceAmount, formatMinutes } from '../utils/format';

type Format = 'a4' | 'thermal';

const dateTime = (iso: string | null | undefined) =>
  iso
    ? new Date(iso).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })
    : '–';
const time = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }) : '–';
const money = formatInvoiceAmount;

/** Plain black-on-white invoice, styled for both A4 paper and 80 mm thermal receipts. */
function InvoiceDocument({ invoice, format }: { invoice: Invoice; format: Format }) {
  const { business, bill, footer } = invoice;
  const thermal = format === 'thermal';
  const gstOn = bill.taxLines.some((t) => t.ratePercent > 0);
  const paid = bill.payments.filter((p) => p.type === 'PAYMENT');
  const refunds = bill.payments.filter((p) => p.type === 'REFUND');
  const deposits = bill.payments.filter((p) => p.type === 'DEPOSIT' || p.type === 'DEPOSIT_REFUND');
  return (
    <div className={`invoice invoice-${format}`}>
      <header className="inv-header">
        <div className="inv-title">{business.name}</div>
        <div className="inv-tagline">{business.tagline}</div>
        {business.address && <div>{business.address}</div>}
        {business.phone && <div>Ph: {business.phone}</div>}
        {gstOn && business.gstin && <div>GSTIN: {business.gstin}</div>}
        <div className="inv-doc">{gstOn ? 'TAX INVOICE' : 'INVOICE'}</div>
      </header>

      {bill.status === 'CANCELLED' && <div className="inv-cancelled">CANCELLED — {bill.cancelReason}</div>}

      <table className="inv-meta">
        <tbody>
          <tr><td>Bill No</td><td>{bill.billNumber}</td></tr>
          <tr><td>Date</td><td>{dateTime(bill.paidAt)}</td></tr>
          <tr><td>Customer</td><td>{bill.customerName ?? 'Walk-in'}</td></tr>
          {bill.customerMobile && <tr><td>Mobile</td><td>{bill.customerMobile}</td></tr>}
        </tbody>
      </table>

      {bill.gaming && (
        <section>
          <div className="inv-section">GAMING</div>
          <table className="inv-meta">
            <tbody>
              <tr><td>Station</td><td>{bill.gaming.stationCode}</td></tr>
              <tr><td>Players</td><td>{bill.gaming.playerCount}</td></tr>
              <tr><td>Start – End</td><td>{time(bill.gaming.startedAt)} – {time(bill.gaming.endedAt)}</td></tr>
              <tr><td>Duration</td><td>{formatMinutes(bill.gaming.actualMinutes ?? 0)}</td></tr>
              {bill.gaming.creditMinutes > 0 && <tr><td>Free credit</td><td>{formatMinutes(bill.gaming.creditMinutes)}</td></tr>}
              <tr><td>Billed</td><td>{formatMinutes(bill.gaming.billableMinutes ?? 0)}</td></tr>
            </tbody>
          </table>
        </section>
      )}

      <table className="inv-lines">
        <thead>
          <tr>
            <th>Item</th>
            <th className="num">Qty</th>
            {!thermal && <th className="num">Rate</th>}
            <th className="num">Amount</th>
          </tr>
        </thead>
        <tbody>
          {bill.lines.map((l) => (
            <tr key={l.id}>
              <td>{l.description}</td>
              <td className="num">{l.quantity}</td>
              {!thermal && <td className="num">{money(l.unitPrice)}</td>}
              <td className="num">{money(l.amount)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <table className="inv-totals">
        <tbody>
          <tr><td>Subtotal</td><td className="num">{money(bill.subtotal)}</td></tr>
          {bill.discountAmount > 0 && (
            <tr><td>Discount{bill.discountName ? ` (${bill.discountName})` : ''}</td><td className="num">{money(-bill.discountAmount)}</td></tr>
          )}
          {gstOn &&
            bill.taxLines
              .filter((t) => t.ratePercent > 0)
              .map((t) => (
                <tr key={t.category}>
                  <td>
                    GST {t.ratePercent}% {t.category.toLowerCase()}
                    {t.priceMode === 'INCLUSIVE' ? ' (incl.)' : ''}
                    {!thermal && t.hsnSacCode ? ` · SAC/HSN ${t.hsnSacCode}` : ''}
                    <div className="inv-small">CGST {money(t.cgst)} + SGST {money(t.sgst)} on {money(t.taxableValue)}</div>
                  </td>
                  <td className="num">{money(t.taxAmount)}</td>
                </tr>
              ))}
          {bill.roundOff !== 0 && <tr><td>Round off</td><td className="num">{money(bill.roundOff)}</td></tr>}
          <tr className="inv-grand"><td>GRAND TOTAL</td><td className="num">{money(bill.totalAmount)}</td></tr>
          {paid.map((p) => (
            <tr key={p.id}><td>Paid by {p.mode}{p.referenceNo ? ` (${p.referenceNo})` : ''}</td><td className="num">{money(p.amount)}</td></tr>
          ))}
          {refunds.map((p) => (
            <tr key={p.id}><td>Refunded ({p.mode})</td><td className="num">{money(-p.amount)}</td></tr>
          ))}
          {deposits.map((p) => (
            <tr key={p.id}>
              <td>{p.type === 'DEPOSIT' ? 'Refundable deposit taken' : 'Deposit returned'} ({p.mode})</td>
              <td className="num">{money(p.type === 'DEPOSIT' ? p.amount : -p.amount)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <footer className="inv-footer">{footer}</footer>
    </div>
  );
}

const PRINT_CSS = `
.invoice { background: #fff; color: #000; font-family: 'Courier New', ui-monospace, monospace; margin: 0 auto; }
.invoice table { width: 100%; border-collapse: collapse; }
.invoice td, .invoice th { padding: 2px 0; vertical-align: top; text-align: left; }
.invoice .num { text-align: right; white-space: nowrap; padding-left: 8px; }
.inv-header { text-align: center; margin-bottom: 8px; }
.inv-title { font-size: 1.6em; font-weight: 800; letter-spacing: 0.12em; }
.inv-tagline { font-weight: 700; letter-spacing: 0.08em; margin-bottom: 4px; }
.inv-doc { margin-top: 6px; font-weight: 700; border-top: 1px dashed #000; border-bottom: 1px dashed #000; padding: 2px 0; }
.inv-section { font-weight: 800; margin-top: 8px; border-bottom: 1px dashed #000; }
.inv-meta td:first-child { width: 40%; color: #333; }
.inv-lines { margin-top: 8px; }
.inv-lines th { border-bottom: 1px solid #000; }
.inv-totals { margin-top: 6px; border-top: 1px dashed #000; }
.inv-grand td { font-weight: 800; font-size: 1.15em; border-top: 1px solid #000; border-bottom: 1px solid #000; padding: 4px 0; }
.inv-small { font-size: 0.8em; color: #333; }
.inv-cancelled { text-align: center; font-weight: 800; border: 2px solid #000; padding: 4px; margin: 6px 0; }
.inv-footer { text-align: center; margin-top: 12px; font-weight: 700; }
.invoice-a4 { width: 180mm; font-size: 12pt; padding: 10mm 0; }
.invoice-thermal { width: 72mm; font-size: 9pt; padding: 2mm 0; }
@media print {
  body { background: #fff !important; }
  .no-print { display: none !important; }
  .print-root { padding: 0 !important; background: #fff !important; }
}
`;

/** /bills/:id/print?format=thermal|a4[&autoprint=1] */
export function BillPrintPage() {
  const { id } = useParams();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const format: Format = params.get('format') === 'a4' ? 'a4' : 'thermal';
  const invoice = useQuery({ queryKey: ['invoice', id], queryFn: () => fetchInvoice(Number(id)) });
  const autoprint = params.get('autoprint') === '1';

  useEffect(() => {
    if (invoice.data && autoprint) {
      const t = window.setTimeout(() => window.print(), 300);
      return () => window.clearTimeout(t);
    }
  }, [invoice.data, autoprint]);

  return (
    <Box className="print-root" sx={{ minHeight: '100vh', bgcolor: '#e5e7eb', p: { xs: 1, sm: 3 } }}>
      <style>{`@page { size: ${format === 'a4' ? 'A4' : '80mm auto'}; margin: ${format === 'a4' ? '12mm' : '3mm'}; }${PRINT_CSS}`}</style>
      <Stack className="no-print" direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1 }} justifyContent="center">
        <Button variant="contained" color="inherit" startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)} sx={{ color: '#111' }}>
          Back
        </Button>
        <ToggleButtonGroup
          exclusive
          size="small"
          value={format}
          onChange={(_, v: Format | null) => v && setParams({ format: v })}
          sx={{
            bgcolor: '#fff',
            '& .MuiToggleButton-root': { color: '#111', fontWeight: 700 },
            '& .MuiToggleButton-root.Mui-selected': { bgcolor: '#111', color: '#fff' },
          }}
        >
          <ToggleButton value="thermal">80 mm receipt</ToggleButton>
          <ToggleButton value="a4">A4</ToggleButton>
        </ToggleButtonGroup>
        <Button variant="contained" startIcon={<PrintIcon />} onClick={() => window.print()} disabled={!invoice.data}>
          Print
        </Button>
      </Stack>
      {invoice.isLoading && (
        <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      )}
      {invoice.isError && (
        <Alert severity="error" className="no-print">
          {toUserMessage(invoice.error)}
        </Alert>
      )}
      {invoice.data && (
        <Box sx={{ bgcolor: '#fff', mx: 'auto', width: 'fit-content', maxWidth: '100%', overflowX: 'auto', px: 2, boxShadow: 3 }}>
          <InvoiceDocument invoice={invoice.data} format={format} />
        </Box>
      )}
    </Box>
  );
}
