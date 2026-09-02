import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://autoyt.github.io',
  base: '/BlockEngine',
  integrations: [
    starlight({
      title: 'BlockEngine',
      description: 'Documentation for the BlockEngine Paper plugin and Java API.',
      logo: {
        src: './src/assets/blockengine-icon.png',
        alt: 'BlockEngine',
      },
      social: [
        {
          icon: 'document',
          label: 'Java API Docs',
          href: '/BlockEngine/reference/java-api/',
        },
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/Autoyt/BlockEngine',
        },
      ],
      customCss: ['./src/styles/custom.css'],
      sidebar: [
        {
          label: 'Start Here',
          items: [
            { label: 'Overview', link: '/' },
          ],
        },
        {
          label: 'Concepts',
          items: [
            { label: 'How BlockEngine Works', slug: 'concepts/how-it-works' },
            { label: 'System Lifecycle', slug: 'concepts/system-lifecycle' },
          ],
        },
        {
          label: 'Data-Driven Blocks',
          items: [
            { label: 'Overview and Setup', slug: 'data-driven' },
            { label: 'Create a Data Pack', slug: 'guides/create-a-data-pack' },
            { label: 'JSON Ruby Block Pack', slug: 'examples/json-ruby-block-pack' },
          ],
        },
        {
          label: 'Code-Driven Blocks',
          items: [
            { label: 'Overview and Setup', slug: 'code-driven' },
            { label: 'Register Blocks from Java', slug: 'guides/register-blocks-from-java' },
            { label: 'Java Lamp Block Plugin', slug: 'examples/java-lamp-block-plugin' },
          ],
        },
        {
          label: 'Shared Systems',
          items: [
            { label: 'Creative Inventory', slug: 'guides/creative-inventory' },
          ],
        },
        {
          label: 'Reference',
          items: [
            { label: 'Data-Driven Packs', slug: 'reference/data-driven-packs' },
            { label: 'Resource Packs', slug: 'reference/resource-packs' },
            { label: 'Commands', slug: 'reference/commands' },
            { label: 'Java API Docs', slug: 'reference/java-api' },
          ],
        },
      ],
    }),
  ],
});
