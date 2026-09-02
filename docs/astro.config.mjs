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
          label: 'Guides',
          items: [
            { label: 'Create a Data Pack', slug: 'guides/create-a-data-pack' },
            { label: 'Register Blocks from Java', slug: 'guides/register-blocks-from-java' },
            { label: 'Creative Inventory', slug: 'guides/creative-inventory' },
          ],
        },
        {
          label: 'End-to-End Examples',
          items: [
            { label: 'JSON Ruby Block Pack', slug: 'examples/json-ruby-block-pack' },
            { label: 'Java Lamp Block Plugin', slug: 'examples/java-lamp-block-plugin' },
          ],
        },
        {
          label: 'Reference',
          items: [
            { label: 'Data-Driven Packs', slug: 'reference/data-driven-packs' },
            { label: 'Java API Docs', link: '/api/' },
          ],
        },
      ],
    }),
  ],
});
