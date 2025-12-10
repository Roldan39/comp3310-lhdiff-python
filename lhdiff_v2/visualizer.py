import html
import os

class HTMLVisualizer:
    """
    Generates a professional side-by-side HTML diff report with Dark Mode support.
    """
    
    HEAD_TEMPLATE = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>LHDiff V2 Execution Report</title>
        <style>
            :root {
                --bg-color: #f6f8fa;
                --container-bg: #ffffff;
                --text-color: #24292f;
                --border-color: #d0d7de;
                --line-num-bg: #ffffff;
                --line-num-text: #6e7781;
                --row-hover: #f6f8fa;
                
                /* Status Colors (Light) */
                --added-bg: #e6ffec; --added-num: #ccffd8;
                --deleted-bg: #ffebe9; --deleted-num: #ffd7d5;
                --modified-bg: #fff8c5; --modified-num: #fff5b1;
                --moved-bg: #ddf4ff; --moved-num: #b6e3ff;
                --empty-bg: #f6f8fa; --empty-text: #ccc;
            }

            [data-theme="dark"] {
                --bg-color: #0d1117;
                --container-bg: #161b22;
                --text-color: #c9d1d9;
                --border-color: #30363d;
                --line-num-bg: #161b22;
                --line-num-text: #8b949e;
                --row-hover: #161b22;

                /* Status Colors (Dark) */
                --added-bg: rgba(46, 160, 67, 0.15); --added-num: rgba(46, 160, 67, 0.4);
                --deleted-bg: rgba(248, 81, 73, 0.15); --deleted-num: rgba(248, 81, 73, 0.4);
                --modified-bg: rgba(210, 153, 34, 0.15); --modified-num: rgba(210, 153, 34, 0.4);
                --moved-bg: rgba(56, 139, 253, 0.15); --moved-num: rgba(56, 139, 253, 0.4);
                --empty-bg: #0d1117; --empty-text: #484f58;
            }

            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif; background-color: var(--bg-color); color: var(--text-color); margin: 0; padding: 20px; transition: background 0.3s; }
            .container { max-width: 1600px; margin: 0 auto; background: var(--container-bg); border: 1px solid var(--border-color); border-radius: 6px; box-shadow: 0 3px 6px rgba(0,0,0,0.04); overflow: hidden; }
            
            /* Header & Toggle */
            .header { background-color: var(--bg-color); padding: 16px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; }
            h2 { margin: 0; font-size: 16px; font-weight: 600; color: var(--text-color); }
            
            .toggle-btn {
                background: none; border: 1px solid var(--border-color); color: var(--text-color);
                padding: 5px 12px; border-radius: 6px; cursor: pointer; font-size: 12px; font-weight: 600;
            }
            .toggle-btn:hover { background-color: var(--border-color); }

            /* Diff Table */
            table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 12px; font-family: 'ui-monospace', 'SFMono-Regular', 'SF Mono', 'Menlo', 'Consolas', 'Liberation Mono', monospace; }
            td { padding: 0; vertical-align: top; line-height: 20px; border-bottom: 1px solid transparent; }
            
            /* Line Numbers */
            .line-num { 
                width: 50px; text-align: right; padding-right: 10px; color: var(--line-num-text); 
                user-select: none; background-color: var(--line-num-bg); border-right: 1px solid var(--border-color);
            }

            /* Code Content */
            .code-content { padding-left: 10px; white-space: pre-wrap; word-break: break-all; color: var(--text-color); }

            /* Status Classes */
            .row-added { background-color: var(--added-bg); }
            .row-added .line-num { background-color: var(--added-num); color: var(--text-color); }
            
            .row-deleted { background-color: var(--deleted-bg); }
            .row-deleted .line-num { background-color: var(--deleted-num); color: var(--text-color); }
            
            .row-modified { background-color: var(--modified-bg); }
            .row-modified .line-num { background-color: var(--modified-num); color: var(--text-color); }
            
            .row-moved { background-color: var(--moved-bg); }
            .row-moved .line-num { background-color: var(--moved-num); color: var(--text-color); }

            .empty { background-color: var(--empty-bg); color: var(--empty-text); }
            
            /* Legend */
            .legend { font-size: 12px; margin-top: 5px; color: var(--line-num-text); }
            .badge { display: inline-block; width: 10px; height: 10px; margin-right: 5px; border-radius: 2px; }
        </style>
        <script>
            function toggleTheme() {
                const html = document.documentElement;
                const current = html.getAttribute('data-theme');
                const newTheme = current === 'dark' ? 'light' : 'dark';
                html.setAttribute('data-theme', newTheme);
                localStorage.setItem('lhdiff-theme', newTheme);
            }
            
            // Apply saved theme on load
            (function() {
                const saved = localStorage.getItem('lhdiff-theme') || 'light';
                document.documentElement.setAttribute('data-theme', saved);
            })();
        </script>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <div>
                    <h2>LHDiff V2: Source Mapping Report</h2>
                    <div class="legend">
                        <span class="badge" style="background:#2ea043"></span>Added
                        <span class="badge" style="background:#f85149; margin-left: 10px;"></span>Deleted
                        <span class="badge" style="background:#d29922; margin-left: 10px;"></span>Modified/Split
                        <span class="badge" style="background:#388bfd; margin-left: 10px;"></span>Moved
                    </div>
                </div>
                <button class="toggle-btn" onclick="toggleTheme()">🌗 Toggle Theme</button>
            </div>
            <table>
                <col width="4%">
                <col width="46%">
                <col width="4%">
                <col width="46%">
    """

    FOOT_TEMPLATE = """
            </table>
        </div>
        <p style="text-align:center; color: #666; font-size: 12px; margin-top: 20px;">Generated by LHDiff V2</p>
    </body>
    </html>
    """

    def generate(self, nodes_a, nodes_b, mappings, output_path="lhdiff_report.html"):
        html_content = [self.HEAD_TEMPLATE]
        map_dict = {m[0]: m[1] for m in mappings}
        
        for node_a in nodes_a:
            idx_a = node_a.original_line_number
            content_a = html.escape(node_a.content)
            mapped_indices = map_dict.get(idx_a, [])
            
            row_class = ""
            if not mapped_indices:
                row_class = "row-deleted"
            elif len(mapped_indices) > 1:
                row_class = "row-modified"
            elif len(mapped_indices) == 1:
                target_node = next((n for n in nodes_b if n.original_line_number == mapped_indices[0]), None)
                if target_node and target_node.content != node_a.content:
                    row_class = "row-modified"
                else:
                    row_class = "row-added"

            right_col_num = ""
            right_col_code = ""
            if mapped_indices:
                new_lines_html = []
                for idx_b in mapped_indices:
                    node_b = next((n for n in nodes_b if n.original_line_number == idx_b), None)
                    if node_b:
                        # Highlight line number in Blue for the link
                        new_lines_html.append(f"<span style='color:#388bfd; font-weight:bold'>[{idx_b}]</span> {html.escape(node_b.content)}")
                right_col_code = "<br>".join(new_lines_html)
                right_col_num = str(mapped_indices[0]) if len(mapped_indices) == 1 else "*"
            else:
                right_col_code = "<span style='font-style:italic; opacity: 0.5'>(Deleted)</span>"

            row_html = f"""
            <tr class="{row_class}">
                <td class="line-num">{idx_a}</td>
                <td class="code-content">{content_a}</td>
                <td class="line-num">{right_col_num}</td>
                <td class="code-content">{right_col_code}</td>
            </tr>
            """
            html_content.append(row_html)

        html_content.append(self.FOOT_TEMPLATE)
        with open(output_path, "w", encoding="utf-8") as f:
            f.write("\n".join(html_content))
        print(f"[Visualizer] Report generated successfully at: {os.path.abspath(output_path)}")