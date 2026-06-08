"use client";

const FORMAT_ROWS = [
  ["PDF / SOP / Job aid", "DOCUMENT", "contentRef URL to document store or HTTPS PDF"],
  ["Video (YouTube, Vimeo, MP4)", "VIDEO", "contentRef watch URL or direct MP4 link"],
  ["Practical competency", "PRACTICAL_TASK", 'JSON checklist array, e.g. ["Step 1","Step 2"]'],
  ["Guided text", "TEXT", "contentBody plain text or markdown"],
  ["Structured blocks", "TEXT", 'contentFormat STRUCTURED_BLOCKS + contentBlocksJson'],
  ["External link", "LINK", "contentRef HTTPS URL"],
] as const;

export function FundoContentFormatsGuide() {
  return (
    <section className="mb-4 rounded-lg border border-teal-200 bg-teal-50/60 p-4 text-sm text-teal-900" data-testid="fundo-content-formats-guide">
      <p className="font-semibold">Governed content formats</p>
      <p className="mt-1 text-xs text-teal-800">
        Upload metadata + reference URL in the library, then bind lessons in authoring admin. See docs/learning/FUNDO_CONTENT_FORMATS.md.
      </p>
      <table className="mt-3 w-full text-left text-xs">
        <thead>
          <tr className="border-b border-teal-200 text-teal-900">
            <th className="py-1 pr-2">Upload type</th>
            <th className="py-1 pr-2">Lesson type</th>
            <th className="py-1">Binding</th>
          </tr>
        </thead>
        <tbody>
          {FORMAT_ROWS.map(([upload, lesson, binding]) => (
            <tr key={upload} className="border-b border-teal-100/80">
              <td className="py-1 pr-2 font-medium">{upload}</td>
              <td className="py-1 pr-2 font-mono">{lesson}</td>
              <td className="py-1 text-teal-800">{binding}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
